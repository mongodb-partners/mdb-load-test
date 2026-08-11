package com.example.loadtest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Bulk-update item: an id plus the fields to set. */
public record CustomerUpdate(
        @NotBlank String id,
        @NotBlank @Email String email,
        @NotBlank String name,
        String phone
) {
}
