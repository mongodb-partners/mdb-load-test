package com.example.loadtest.dto;

/** A single vector-search result with its similarity score. */
public record VectorSearchHit(
        String id,
        String sku,
        String name,
        String category,
        double score
) {
}
