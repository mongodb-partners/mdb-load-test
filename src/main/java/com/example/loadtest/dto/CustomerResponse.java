package com.example.loadtest.dto;

import com.example.loadtest.model.Customer;

import java.time.Instant;

/** Customer view returned to clients. */
public record CustomerResponse(
        String id,
        String email,
        String name,
        String phone,
        Instant createdAt
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getEmail(), c.getName(), c.getPhone(), c.getCreatedAt());
    }
}
