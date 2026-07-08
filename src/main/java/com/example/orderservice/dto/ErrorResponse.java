package com.example.orderservice.dto;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error shape for every 4xx/5xx response.
 *
 * {
 *   "timestamp": "...",
 *   "status": 404,
 *   "error": "NOT_FOUND",
 *   "message": "Order 123 not found",
 *   "path": "/api/orders/123",
 *   "details": []            // populated for field-validation errors
 * }
 */
public class ErrorResponse {

    private final Instant timestamp = Instant.now();
    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final List<String> details;

    public ErrorResponse(int status, String error, String message, String path) {
        this(status, error, message, path, List.of());
    }

    public ErrorResponse(int status, String error, String message, String path, List<String> details) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.details = details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public List<String> getDetails() {
        return details;
    }
}
