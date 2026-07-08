package com.example.orderservice.dto;

import com.example.orderservice.model.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public class OrderItemResponse {

    private final UUID id;
    private final String productName;
    private final Integer quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal lineTotal;

    public OrderItemResponse(OrderItem item) {
        this.id = item.getId();
        this.productName = item.getProductName();
        this.quantity = item.getQuantity();
        this.unitPrice = item.getUnitPrice();
        this.lineTotal = item.lineTotal();
    }

    public UUID getId() {
        return id;
    }

    public String getProductName() {
        return productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
