package com.example.loadtest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/** Bulk-update item: an id plus the fields to set. */
public record ProductUpdate(
        @NotBlank String id,
        @NotBlank String sku,
        @NotBlank String name,
        String description,
        @NotBlank String category,
        @PositiveOrZero double price
) {
}
