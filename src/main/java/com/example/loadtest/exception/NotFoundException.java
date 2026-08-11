package com.example.loadtest.exception;

/** Thrown when an entity lookup by id finds nothing (maps to HTTP 404). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
