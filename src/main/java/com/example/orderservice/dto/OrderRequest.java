package com.example.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Used for both create (POST) and update (PUT). orderId, status, createdAt,
 * updatedAt and totalAmount are intentionally absent - they are server-managed
 * and cannot be set by the client (see application.yml:
 * fail-on-unknown-properties=true, which turns any attempt to send them into
 * a 400 rather than a silent no-op).
 */
public class OrderRequest {

    @NotBlank(message = "customerName must not be blank")
    private String customerName;

    @NotEmpty(message = "items must contain at least one line item")
    @Valid
    private List<OrderItemRequest> items;

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public List<OrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }
}
