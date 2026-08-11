package com.example.loadtest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/** Create payload for an order. totalAmount is computed server-side. */
public record OrderRequest(
        @NotBlank String customerId,
        @NotEmpty @Valid List<Item> items,
        String status
) {
    public record Item(
            @NotBlank String productId,
            @Positive int quantity,
            @PositiveOrZero double unitPrice
    ) {
    }
}
