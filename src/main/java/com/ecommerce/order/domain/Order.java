package com.ecommerce.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Order {

    private final String id;
    private final String customerId;
    private final List<OrderItem> items;
    private OrderStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public Order(String customerId, List<OrderItem> initialItems) {
        this(UUID.randomUUID().toString(), customerId, initialItems, OrderStatus.PENDING, Instant.now());
    }

    public Order(String id, String customerId, List<OrderItem> items, OrderStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.customerId = Objects.requireNonNull(customerId);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Must have at least one item");
        }
        String currency = items.getFirst().getUnitPrice().currency();
        if (!items.stream().allMatch(item -> item.getUnitPrice().currency().equalsIgnoreCase(currency))) {
            throw new IllegalArgumentException("All items must use the same currency");
        }
        this.items = new ArrayList<>(items);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = createdAt;
    }

    public Money calculateTotal() {
        Money total = new Money(BigDecimal.ZERO, items.getFirst().getUnitPrice().currency());
        for (OrderItem item : items) {
            total = total.add(item.calculateSubtotal());
        }
        return total;
    }

    public void transitionTo(OrderStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException("Illegal state transition from " + this.status + " to " + newStatus);
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public void markAsPaid() {
        transitionTo(OrderStatus.PAID);
    }

    public void cancel() {
        transitionTo(OrderStatus.CANCELLED);
    }

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
