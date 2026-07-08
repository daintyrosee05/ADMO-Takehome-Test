package com.example.orderservice.dto;

import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class OrderResponse {

    private final UUID orderId;
    private final String customerName;
    private final List<OrderItemResponse> items;
    private final OrderStatus status;
    private final BigDecimal totalAmount;
    private final String cancellationReason;
    private final Instant createdAt;
    private final Instant updatedAt;

    public OrderResponse(Order order) {
        this.orderId = order.getOrderId();
        this.customerName = order.getCustomerName();
        this.items = order.getItems().stream().map(OrderItemResponse::new).collect(Collectors.toList());
        this.status = order.getStatus();
        this.totalAmount = order.getTotalAmount();
        this.cancellationReason = order.getCancellationReason();
        this.createdAt = order.getCreatedAt();
        this.updatedAt = order.getUpdatedAt();
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public List<OrderItemResponse> getItems() {
        return items;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
