package com.example.loadtest.dto;

import com.example.loadtest.model.Product;

/** Product view returned to clients (embedding intentionally omitted). */
public record ProductResponse(
        String id,
        String sku,
        String name,
        String description,
        String category,
        double price
) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(),
                p.getDescription(), p.getCategory(), p.getPrice());
    }
}
