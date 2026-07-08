package com.example.orderservice.exception;

/**
 * Thrown for domain-level validation failures that aren't expressible as
 * simple bean-validation annotations (e.g. "items are immutable after
 * payment"). Mapped to 400 Bad Request / 409 Conflict depending on context
 * by the global handler.
 */
public class InvalidOrderException extends RuntimeException {
    public InvalidOrderException(String message) {
        super(message);
    }
}
