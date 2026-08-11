package com.example.loadtest.seed;

import com.example.loadtest.config.AppProperties;
import com.example.loadtest.model.Customer;
import com.example.loadtest.model.Order;
import com.example.loadtest.model.Product;
import com.example.loadtest.repository.CustomerRepository;
import com.example.loadtest.repository.OrderRepository;
import com.example.loadtest.repository.ProductRepository;
import com.example.loadtest.service.EmbeddingClient;
import com.example.loadtest.service.ProductService;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Seeds MongoDB with products (+ embeddings), customers, and orders, and creates
 * a standard Atlas Vector Search index on the products collection.
 *
 * <p>Embeddings are computed by the app via {@link EmbeddingClient} (Voyage via
 * {@code https://ai.mongodb.com/v1/embeddings}) and stored in {@code embedding}.
 * A plain {@code vector}-type index is created over that field — this needs NO
 * Automated Embedding / storage auto-scaling on the cluster.
 *
 * <p>Sizing is configurable (see {@code app.seed.*}); defaults are 1,000,000
 * products / 10,000 customers / 20,000 orders. Products are embedded in bounded
 * parallel waves and inserted in batches, so heap use stays flat.
 *
 * <p>Run with the {@code seed} profile:
 * <pre>mvn spring-boot:run -Dspring-boot.run.profiles=seed</pre>
 * Re-create only the index (data already embedded) with {@code SEED_INDEX_ONLY=true}.
 */
@Component
@Profile("seed")
public class SeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private static final int VOYAGE_MAX_BATCH = 128;

    private static final String[] ADJECTIVES = {
            "Wireless", "Ergonomic", "Portable", "Rechargeable", "Compact", "Premium",
            "Ultra-thin", "Noise-cancelling", "Waterproof", "Smart", "Stainless-steel", "Organic"};
    private static final String[] NOUNS = {
            "Headphones", "Keyboard", "Water Bottle", "Backpack", "Desk Lamp", "Coffee Mug",
            "Running Shoes", "Yoga Mat", "Wristwatch", "Bluetooth Speaker", "Office Chair", "Notebook"};
    private static final String[] CATEGORIES = {
            "electronics", "home", "sports", "office", "outdoors", "kitchen"};

    private final AppProperties props;
    private final MongoTemplate mongoTemplate;
    private final EmbeddingClient embeddingClient;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    public SeedRunner(AppProperties props,
                      MongoTemplate mongoTemplate,
                      EmbeddingClient embeddingClient,
                      ProductRepository productRepository,
                      CustomerRepository customerRepository,
                      OrderRepository orderRepository) {
        this.props = props;
        this.mongoTemplate = mongoTemplate;
        this.embeddingClient = embeddingClient;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public void run(String... args) {
        AppProperties.Seed s = props.getSeed();

        if (s.isIndexOnly()) {
            log.info("index-only=true: (re)creating the vector index without touching data.");
            ensureVectorSearchIndex();
            log.info("Done.");
            return;
        }

        log.info("Seeding: {} products, {} customers, {} orders (embeddings={}, insertBatch={}, embedConcurrency={}).",
                s.getProducts(), s.getCustomers(), s.getOrders(),
                s.isGenerateEmbeddings(), s.getInsertBatchSize(), s.getEmbedConcurrency());

        log.info("Clearing existing collections (keeping indexes)...");
        productRepository.deleteAll();
        customerRepository.deleteAll();
        orderRepository.deleteAll();

        List<String> customerIds = seedCustomers(s.getCustomers(), s.getInsertBatchSize());
        List<String> productSample = seedProducts(s);
        seedOrders(s.getOrders(), s.getInsertBatchSize(), productSample, customerIds);
        ensureVectorSearchIndex();

        log.info("Seeding complete.");
    }

    // ---- customers ---------------------------------------------------------

    private List<String> seedCustomers(int count, int batchSize) {
        List<String> ids = new ArrayList<>(count);
        List<Customer> buffer = new ArrayList<>(batchSize);
        for (int i = 0; i < count; i++) {
            Customer c = new Customer();
            c.setEmail(String.format("customer%07d@example.com", i));
            c.setName("Customer " + i);
            c.setPhone(String.format("555-%07d", i));
            c.setCreatedAt(Instant.now());
            buffer.add(c);
            if (buffer.size() >= batchSize) {
                flushCustomers(buffer, ids);
            }
        }
        flushCustomers(buffer, ids);
        log.info("Inserted {} customers.", ids.size());
        return ids;
    }

    private void flushCustomers(List<Customer> buffer, List<String> ids) {
        if (buffer.isEmpty()) {
            return;
        }
        mongoTemplate.insert(buffer, Customer.class);
        for (Customer c : buffer) {
            ids.add(c.getId());
        }
        buffer.clear();
    }

    // ---- products (streaming + parallel embedding) -------------------------

    private List<String> seedProducts(AppProperties.Seed s) {
        int count = s.getProducts();
        int sampleCap = Math.min(s.getProductIdSampleCap(), count);
        List<String> sample = new ArrayList<>(sampleCap);

        if (!s.isGenerateEmbeddings()) {
            log.warn("generate-embeddings=false: products will have NO embeddings; vector search will not match them.");
            List<Product> buffer = new ArrayList<>(s.getInsertBatchSize());
            long inserted = 0;
            for (int i = 0; i < count; i++) {
                buffer.add(buildProduct(i));
                if (buffer.size() >= s.getInsertBatchSize()) {
                    inserted += flushProducts(buffer, sample, sampleCap);
                }
            }
            inserted += flushProducts(buffer, sample, sampleCap);
            log.info("Inserted {} products (no embeddings).", inserted);
            return sample;
        }

        int voyageBatch = Math.min(Math.max(1, props.getEmbedding().getBatchSize()), VOYAGE_MAX_BATCH);
        int concurrency = Math.max(1, s.getEmbedConcurrency());
        int totalBatches = (count + voyageBatch - 1) / voyageBatch;
        log.info("Embedding {} products via '{}' ({}) in {} batches of {} ({} concurrent). This can take a while.",
                count, props.getEmbedding().getModel(), props.getEmbedding().getBaseUrl(),
                totalBatches, voyageBatch, concurrency);

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Product> insertBuffer = new ArrayList<>(s.getInsertBatchSize());
        long inserted = 0;
        try {
            for (int base = 0; base < totalBatches; base += concurrency) {
                int waveEnd = Math.min(base + concurrency, totalBatches);
                List<Future<List<Product>>> futures = new ArrayList<>(waveEnd - base);
                for (int b = base; b < waveEnd; b++) {
                    int start = b * voyageBatch;
                    int end = Math.min(start + voyageBatch, count);
                    futures.add(pool.submit(() -> buildAndEmbedBatch(start, end)));
                }
                for (Future<List<Product>> future : futures) {
                    insertBuffer.addAll(future.get());
                    if (insertBuffer.size() >= s.getInsertBatchSize()) {
                        inserted += flushProducts(insertBuffer, sample, sampleCap);
                    }
                }
                log.info("Products progress: ~{}/{} inserted.", Math.min(waveEnd * (long) voyageBatch, count), count);
            }
            inserted += flushProducts(insertBuffer, sample, sampleCap);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while seeding products.", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Product embedding/insert failed.", e.getCause());
        } finally {
            pool.shutdown();
        }
        log.info("Inserted {} products (with embeddings).", inserted);
        return sample;
    }

    private List<Product> buildAndEmbedBatch(int start, int end) {
        List<Product> batch = new ArrayList<>(end - start);
        List<String> texts = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            Product p = buildProduct(i);
            batch.add(p);
            texts.add(ProductService.embedText(p.getName(), p.getDescription()));
        }
        List<float[]> vectors = embeddingClient.embedDocuments(texts);
        for (int j = 0; j < batch.size(); j++) {
            batch.get(j).setEmbedding(vectors.get(j));
        }
        return batch;
    }

    private Product buildProduct(int i) {
        String adj = ADJECTIVES[i % ADJECTIVES.length];
        String noun = NOUNS[(i / ADJECTIVES.length) % NOUNS.length];
        Product p = new Product();
        p.setSku(String.format("SKU-%08d", i));
        p.setName(adj + " " + noun);
        p.setDescription("A " + adj.toLowerCase() + " " + noun.toLowerCase() + " (unit #" + i
                + ") built for everyday use with excellent build quality.");
        p.setCategory(CATEGORIES[i % CATEGORIES.length]);
        p.setPrice(Math.round((5 + ThreadLocalRandom.current().nextDouble() * 495) * 100.0) / 100.0);
        return p;
    }

    /** Inserts the buffer, records sample ids (up to the cap), clears the buffer, returns count inserted. */
    private int flushProducts(List<Product> buffer, List<String> sample, int sampleCap) {
        if (buffer.isEmpty()) {
            return 0;
        }
        mongoTemplate.insert(buffer, Product.class);
        for (Product p : buffer) {
            if (sample.size() < sampleCap) {
                sample.add(p.getId());
            }
        }
        int n = buffer.size();
        buffer.clear();
        return n;
    }

    // ---- orders ------------------------------------------------------------

    private void seedOrders(int count, int batchSize, List<String> productIds, List<String> customerIds) {
        if (productIds.isEmpty() || customerIds.isEmpty()) {
            log.warn("Skipping order seeding: no product/customer ids available to reference.");
            return;
        }
        List<Order> buffer = new ArrayList<>(batchSize);
        long inserted = 0;
        for (int i = 0; i < count; i++) {
            Order o = new Order();
            o.setCustomerId(customerIds.get(ThreadLocalRandom.current().nextInt(customerIds.size())));
            int itemCount = 1 + ThreadLocalRandom.current().nextInt(4);
            List<Order.OrderItem> items = new ArrayList<>(itemCount);
            double total = 0;
            for (int k = 0; k < itemCount; k++) {
                Order.OrderItem item = new Order.OrderItem();
                item.setProductId(productIds.get(ThreadLocalRandom.current().nextInt(productIds.size())));
                item.setQuantity(1 + ThreadLocalRandom.current().nextInt(3));
                item.setUnitPrice(Math.round((5 + ThreadLocalRandom.current().nextDouble() * 495) * 100.0) / 100.0);
                total += item.getQuantity() * item.getUnitPrice();
                items.add(item);
            }
            o.setItems(items);
            o.setStatus("NEW");
            o.setTotalAmount(Math.round(total * 100.0) / 100.0);
            o.setCreatedAt(Instant.now());
            buffer.add(o);
            if (buffer.size() >= batchSize) {
                mongoTemplate.insert(buffer, Order.class);
                inserted += buffer.size();
                buffer = new ArrayList<>(batchSize);
            }
        }
        if (!buffer.isEmpty()) {
            mongoTemplate.insert(buffer, Order.class);
            inserted += buffer.size();
        }
        log.info("Inserted {} orders.", inserted);
    }

    // ---- vector search index (standard "vector" type) ----------------------

    private void ensureVectorSearchIndex() {
        AppProperties.Vector cfg = props.getVector();
        String collectionName = mongoTemplate.getCollectionName(Product.class);
        MongoCollection<Document> collection = mongoTemplate.getCollection(collectionName);

        // Idempotent: skip if an index with the configured name already exists.
        try {
            for (Document existing : collection.listSearchIndexes()) {
                if (cfg.getIndexName().equals(existing.getString("name"))) {
                    log.info("Vector search index '{}' already exists — skipping creation.", cfg.getIndexName());
                    return;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not list search indexes ({}). Attempting creation anyway.", e.getMessage());
        }

        // Standard vector field over app-computed embeddings. Created via the raw
        // createSearchIndexes command so it works regardless of driver version.
        Document field = new Document("type", "vector")
                .append("path", cfg.getPath())
                .append("numDimensions", cfg.getNumDimensions())
                .append("similarity", cfg.getSimilarity());
        if (cfg.getQuantization() != null && !cfg.getQuantization().isBlank()) {
            field.append("quantization", cfg.getQuantization());
        }

        Document command = new Document("createSearchIndexes", collectionName)
                .append("indexes", List.of(new Document("name", cfg.getIndexName())
                        .append("type", "vectorSearch")
                        .append("definition", new Document("fields", List.of(field)))));

        try {
            mongoTemplate.getDb().runCommand(command);
            log.info("Created vector index '{}' ({} dims, {}, quantization={}). Builds asynchronously in Atlas.",
                    cfg.getIndexName(), cfg.getNumDimensions(), cfg.getSimilarity(), cfg.getQuantization());
        } catch (RuntimeException e) {
            log.warn("Vector index creation failed (may already exist or require Atlas): {}", e.getMessage());
        }
    }
}
