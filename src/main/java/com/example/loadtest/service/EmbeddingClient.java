package com.example.loadtest.service;

import com.example.loadtest.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Calls the Voyage embeddings API. An Atlas-issued (model) API key authenticates
 * to {@code https://ai.mongodb.com/v1/embeddings}; a key created on VoyageAI
 * directly authenticates to {@code https://api.voyageai.com/v1/embeddings}. The
 * key is validated lazily so the app starts (and simulated mode / non-search
 * traffic works) without one.
 */
@Component
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    private final AppProperties.Embedding cfg;
    private final RestClient restClient;

    public EmbeddingClient(AppProperties props) {
        this.cfg = props.getEmbedding();
        this.restClient = RestClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + (cfg.getApiKey() == null ? "" : cfg.getApiKey()))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public boolean hasApiKey() {
        return cfg.getApiKey() != null && !cfg.getApiKey().isBlank();
    }

    /** Embeds documents (for indexing). Returns one vector per input, in order. */
    public List<float[]> embedDocuments(List<String> texts) {
        return embed(texts, "document");
    }

    /** Embeds a single query string (for search). */
    public float[] embedQuery(String text) {
        return embed(List.of(text), "query").get(0);
    }

    private List<float[]> embed(List<String> texts, String inputType) {
        if (!hasApiKey()) {
            throw new IllegalStateException("Embedding API key is not set (app.embedding.api-key / "
                    + "EMBEDDING_API_KEY). Required to generate embeddings.");
        }
        Map<String, Object> body = Map.of(
                "model", cfg.getModel(),
                "input", texts,
                "input_type", inputType,
                "output_dimension", cfg.getDimensions(),
                "output_dtype", "float");

        int maxAttempts = Math.max(1, cfg.getMaxRetries());
        RuntimeException last = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return callOnce(body);
            } catch (HttpClientErrorException.TooManyRequests | HttpServerErrorException e) {
                last = e;
                long backoffMs = Math.min(30_000L, (long) (500L * Math.pow(2, attempt)));
                log.warn("Embedding call failed (attempt {}/{}, status {}). Retrying in {} ms.",
                        attempt + 1, maxAttempts, e.getStatusCode().value(), backoffMs);
                sleep(backoffMs);
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                throw new IllegalStateException("Embedding API rejected the key (HTTP "
                        + e.getStatusCode().value() + "). An Atlas-issued key must target "
                        + "https://ai.mongodb.com/v1/embeddings; a VoyageAI key must target "
                        + "https://api.voyageai.com/v1/embeddings. Check app.embedding.base-url and the key. "
                        + "Response: " + e.getResponseBodyAsString(), e);
            }
        }
        throw new IllegalStateException("Embedding failed after " + maxAttempts + " attempts.", last);
    }

    private List<float[]> callOnce(Map<String, Object> body) {
        EmbeddingResponse response = restClient.post()
                .body(body)
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.data() == null) {
            throw new IllegalStateException("Empty embeddings response.");
        }
        return response.data().stream()
                .sorted((a, b) -> Integer.compare(a.index(), b.index()))
                .map(EmbeddingData::toFloatArray)
                .toList();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off embedding retry.", e);
        }
    }

    private record EmbeddingResponse(String model, List<EmbeddingData> data, Object usage) {
    }

    private record EmbeddingData(int index, List<Double> embedding) {
        float[] toFloatArray() {
            float[] out = new float[embedding.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = embedding.get(i).floatValue();
            }
            return out;
        }
    }
}
