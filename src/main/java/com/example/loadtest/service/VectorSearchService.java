package com.example.loadtest.service;

import com.example.loadtest.config.AppProperties;
import com.example.loadtest.dto.VectorSearchHit;
import com.example.loadtest.dto.VectorSearchRequest;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs Atlas {@code $vectorSearch} against the products collection. The query
 * vector is either supplied directly (req.vector) or computed from req.query via
 * {@link EmbeddingClient}. Routed through the same {@code app.use-db} toggle as
 * the CRUD services.
 */
@Service
public class VectorSearchService extends BaseDataService {

    private final MongoTemplate mongoTemplate;
    private final EmbeddingClient embeddingClient;

    public VectorSearchService(AppProperties props, MongoTemplate mongoTemplate, EmbeddingClient embeddingClient) {
        super(props);
        this.mongoTemplate = mongoTemplate;
        this.embeddingClient = embeddingClient;
    }

    public List<VectorSearchHit> search(VectorSearchRequest req) {
        if (!req.hasVector() && !req.hasQuery()) {
            throw new IllegalArgumentException("Provide either 'query' text or a 'vector'.");
        }
        return execute(() -> runVectorSearch(req), () -> simulated(req.limitOrDefault()));
    }

    private List<VectorSearchHit> runVectorSearch(VectorSearchRequest req) {
        AppProperties.Vector cfg = props.getVector();

        // Use the supplied vector, or embed the query text via the embedding API.
        List<Float> queryVector = req.hasVector() ? req.vector() : toFloatList(embeddingClient.embedQuery(req.query()));

        Document vectorSearch = new Document("$vectorSearch", new Document()
                .append("index", cfg.getIndexName())
                .append("path", cfg.getPath())
                .append("queryVector", queryVector)
                .append("numCandidates", req.numCandidatesOrDefault())
                .append("limit", req.limitOrDefault()));

        Document project = new Document("$project", new Document()
                .append("sku", 1)
                .append("name", 1)
                .append("category", 1)
                .append("score", new Document("$meta", "vectorSearchScore")));

        List<Document> pipeline = List.of(vectorSearch, project);

        List<VectorSearchHit> hits = new ArrayList<>();
        mongoTemplate.getCollection(mongoTemplate.getCollectionName(com.example.loadtest.model.Product.class))
                .aggregate(pipeline)
                .forEach(doc -> hits.add(new VectorSearchHit(
                        String.valueOf(doc.get("_id")),
                        doc.getString("sku"),
                        doc.getString("name"),
                        doc.getString("category"),
                        doc.get("score") == null ? 0.0 : ((Number) doc.get("score")).doubleValue())));
        return hits;
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> out = new ArrayList<>(arr.length);
        for (float v : arr) {
            out.add(v);
        }
        return out;
    }

    private List<VectorSearchHit> simulated(int limit) {
        List<VectorSearchHit> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            out.add(new VectorSearchHit("sim-" + i, "SIM-SKU-" + i, "Simulated Product " + i, "sim", 1.0 - i * 0.01));
        }
        return out;
    }
}
