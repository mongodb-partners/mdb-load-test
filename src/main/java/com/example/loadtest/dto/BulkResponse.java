package com.example.loadtest.dto;

import java.util.List;

/** Result of a bulk write. */
public record BulkResponse(int requested, int succeeded, List<String> ids) {
}
