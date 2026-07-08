package com.example.orderservice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body required for the cancel transition. Cancelling is the only current
 * transition that needs extra data; each future transition that needs its
 * own data gets its own request DTO + endpoint rather than one bloated
 * "PATCH status" payload with optional fields for every rule.
 */
public class CancelRequest {

    @NotBlank(message = "reason is required to cancel an order")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
