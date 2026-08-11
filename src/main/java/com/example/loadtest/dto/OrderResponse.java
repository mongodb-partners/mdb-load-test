package com.example.loadtest.dto;

import com.example.loadtest.model.Order;

import java.time.Instant;
import java.util.List;

/** Order view returned to clients. */
public record OrderResponse(
        String id,
        String customerId,
        List<Item> items,
        String status,
        double totalAmount,
        Instant createdAt
) {
    public record Item(String productId, int quantity, double unitPrice) {
    }

    public static OrderResponse from(Order o) {
        List<Item> items = o.getItems() == null ? List.of() : o.getItems().stream()
                .map(i -> new Item(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new OrderResponse(o.getId(), o.getCustomerId(), items,
                o.getStatus(), o.getTotalAmount(), o.getCreatedAt());
    }
}
