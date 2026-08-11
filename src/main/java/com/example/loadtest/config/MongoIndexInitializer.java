package com.example.loadtest.config;

import com.example.loadtest.model.Customer;
import com.example.loadtest.model.Order;
import com.example.loadtest.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Ensures the regular (non-vector) indexes exist on startup. Idempotent — Mongo
 * ignores re-creation of an identical index.
 *
 * <p>Skipped entirely when {@code app.use-db=false}, since simulated mode never
 * touches MongoDB.
 *
 * <p>The Atlas Vector Search index is NOT created here — it requires the Atlas
 * search-index API and is created by the seed runner (see {@code SeedRunner}).
 */
@Component
@ConditionalOnProperty(name = "app.use-db", havingValue = "true", matchIfMissing = true)
public class MongoIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoTemplate mongoTemplate;

    public MongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        // Products: unique sku, category filter
        IndexOperations products = mongoTemplate.indexOps(Product.class);
        products.ensureIndex(new Index().on("sku", Sort.Direction.ASC).unique().named("uniq_sku"));
        products.ensureIndex(new Index().on("category", Sort.Direction.ASC).named("idx_category"));

        // Orders: lookups by customer, status, and recency
        IndexOperations orders = mongoTemplate.indexOps(Order.class);
        orders.ensureIndex(new Index().on("customerId", Sort.Direction.ASC).named("idx_customerId"));
        orders.ensureIndex(new Index().on("status", Sort.Direction.ASC).named("idx_status"));
        orders.ensureIndex(new Index().on("createdAt", Sort.Direction.DESC).named("idx_createdAt"));

        // Customers: unique email
        IndexOperations customers = mongoTemplate.indexOps(Customer.class);
        customers.ensureIndex(new Index().on("email", Sort.Direction.ASC).unique().named("uniq_email"));

        log.info("Regular MongoDB indexes ensured for products, orders, customers.");
    }
}
