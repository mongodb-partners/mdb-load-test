package com.example.loadtest.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Body for bulk read-by-ids. */
public record BulkIdsRequest(@NotEmpty List<String> ids) {
}
