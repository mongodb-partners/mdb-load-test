package com.example.loadtest.dto;

import java.util.List;

/**
 * Vector search request. Provide either:
 *   - {@code query}: natural-language text — the app embeds it, or
 *   - {@code vector}: a pre-computed embedding (1024 dims) to search directly
 *     (skips the embedding call — useful to isolate DB latency in load tests).
 * If both are present, {@code vector} wins.
 */
public record VectorSearchRequest(
        String query,
        List<Float> vector,
        Integer limit,
        Integer numCandidates
) {
    public boolean hasVector() {
        return vector != null && !vector.isEmpty();
    }

    public boolean hasQuery() {
        return query != null && !query.isBlank();
    }

    public int limitOrDefault() {
        return limit != null && limit > 0 ? limit : 10;
    }

    public int numCandidatesOrDefault() {
        return numCandidates != null && numCandidates > 0 ? numCandidates : Math.max(100, limitOrDefault() * 10);
    }
}
