package com.ecommerce.order.domain.model;

import java.util.UUID;

/** Value object: identity of an {@link Order}. A distinct type, so it cannot be swapped with a customer id. */
public record OrderId(String value) {

    private static final int MAX_LENGTH = 64;

    public OrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Order ID must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Order ID must be at most " + MAX_LENGTH + " characters");
        }
    }

    public static OrderId generate() {
        return new OrderId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
