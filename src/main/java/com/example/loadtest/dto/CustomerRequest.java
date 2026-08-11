package com.example.loadtest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Create/update payload for a customer. */
public record CustomerRequest(
        @NotBlank @Email String email,
        @NotBlank String name,
        String phone
) {
}
