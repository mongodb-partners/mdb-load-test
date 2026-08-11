package com.example.loadtest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/** Create/update payload for a product. */
public record ProductRequest(
        @NotBlank String sku,
        @NotBlank String name,
        String description,
        @NotBlank String category,
        @PositiveOrZero double price
) {
}
