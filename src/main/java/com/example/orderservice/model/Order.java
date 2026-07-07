package com.example.orderservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID orderId;

    @Column(nullable = false)
    private String customerName;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    // Populated only when status = CANCELLED. Modeled as a plain field for
    // now; if more transitions gain metadata (e.g. a "return reason" on
    // DELIVERED), this should move to a small per-transition metadata table
    // instead of growing this entity with one column per rule.
    private String cancellationReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Order() {
        // JPA
    }

    public Order(String customerName) {
        this.customerName = customerName;
        // Set here (not only in @PrePersist) so the entity has valid
        // timestamps immediately when constructed directly in unit tests,
        // without needing a real JPA persistence lifecycle.
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = OrderStatus.CREATED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void clearItems() {
        items.clear();
    }

    public void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
