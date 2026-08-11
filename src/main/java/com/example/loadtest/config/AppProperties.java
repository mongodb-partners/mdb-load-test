package com.example.loadtest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Binds the {@code app.*} settings from application.yml.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * true = hit MongoDB; false = skip DB and return a canned response after
     * simulatedWaitMs.
     */
    private boolean useDb = true;

    /** Fixed baseline latency (ms) applied when useDb=false. */
    private long simulatedWaitMs = 50;

    @NestedConfigurationProperty
    private Vector vector = new Vector();

    @NestedConfigurationProperty
    private Embedding embedding = new Embedding();

    @NestedConfigurationProperty
    private Seed seed = new Seed();

    public boolean isUseDb() {
        return useDb;
    }

    public void setUseDb(boolean useDb) {
        this.useDb = useDb;
    }

    public long getSimulatedWaitMs() {
        return simulatedWaitMs;
    }

    public void setSimulatedWaitMs(long simulatedWaitMs) {
        this.simulatedWaitMs = simulatedWaitMs;
    }

    public Vector getVector() {
        return vector;
    }

    public void setVector(Vector vector) {
        this.vector = vector;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    public Seed getSeed() {
        return seed;
    }

    public void setSeed(Seed seed) {
        this.seed = seed;
    }

    /**
     * Standard Atlas Vector Search index geometry. Embeddings are computed by the
     * app (see {@link Embedding}) and stored at {@code path}.
     */
    public static class Vector {
        private String indexName = "products_vector_index";
        private String path = "embedding";
        private int numDimensions = 1024;
        private String similarity = "cosine";
        /** none | scalar | binary (for the stored vector index). */
        private String quantization = "none";

        public String getIndexName() {
            return indexName;
        }

        public void setIndexName(String indexName) {
            this.indexName = indexName;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public int getNumDimensions() {
            return numDimensions;
        }

        public void setNumDimensions(int numDimensions) {
            this.numDimensions = numDimensions;
        }

        public String getSimilarity() {
            return similarity;
        }

        public void setSimilarity(String similarity) {
            this.similarity = similarity;
        }

        public String getQuantization() {
            return quantization;
        }

        public void setQuantization(String quantization) {
            this.quantization = quantization;
        }
    }

    /**
     * Embedding API settings. An Atlas-issued (model) API key authenticates to
     * {@code https://ai.mongodb.com/v1/embeddings}; a key created directly on
     * VoyageAI authenticates to {@code https://api.voyageai.com/v1/embeddings}.
     */
    public static class Embedding {
        private String apiKey = "";
        private String baseUrl = "https://ai.mongodb.com/v1/embeddings";
        private String model = "voyage-4";
        private int dimensions = 1024;
        private int batchSize = 128;
        private int maxRetries = 5;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getDimensions() {
            return dimensions;
        }

        public void setDimensions(int dimensions) {
            this.dimensions = dimensions;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }
    }

    /** Seed runner sizing (profile "seed" only). */
    public static class Seed {
        private int products = 1_000_000;
        private int customers = 10_000;
        private int orders = 20_000;
        /** Docs per Mongo bulk insert. */
        private int insertBatchSize = 1_000;
        /** Concurrent embedding batches in flight. */
        private int embedConcurrency = 4;
        /** When false, products are inserted without embeddings (fast; no vector search). */
        private boolean generateEmbeddings = true;
        /** Max product ids retained in memory to reference from seeded orders. */
        private int productIdSampleCap = 50_000;
        /** When true, only (re)create the vector index; skip clearing/inserting data. */
        private boolean indexOnly = false;

        public int getProducts() {
            return products;
        }

        public void setProducts(int products) {
            this.products = products;
        }

        public int getCustomers() {
            return customers;
        }

        public void setCustomers(int customers) {
            this.customers = customers;
        }

        public int getOrders() {
            return orders;
        }

        public void setOrders(int orders) {
            this.orders = orders;
        }

        public int getInsertBatchSize() {
            return insertBatchSize;
        }

        public void setInsertBatchSize(int insertBatchSize) {
            this.insertBatchSize = insertBatchSize;
        }

        public int getEmbedConcurrency() {
            return embedConcurrency;
        }

        public void setEmbedConcurrency(int embedConcurrency) {
            this.embedConcurrency = embedConcurrency;
        }

        public boolean isGenerateEmbeddings() {
            return generateEmbeddings;
        }

        public void setGenerateEmbeddings(boolean generateEmbeddings) {
            this.generateEmbeddings = generateEmbeddings;
        }

        public int getProductIdSampleCap() {
            return productIdSampleCap;
        }

        public void setProductIdSampleCap(int productIdSampleCap) {
            this.productIdSampleCap = productIdSampleCap;
        }

        public boolean isIndexOnly() {
            return indexOnly;
        }

        public void setIndexOnly(boolean indexOnly) {
            this.indexOnly = indexOnly;
        }
    }
}
