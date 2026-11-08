package com.ecommerce.order.domain.model;

/** Value object: reference to the customer who owns an order. The customer itself lives in another context. */
public record CustomerId(String value) {

    private static final int MAX_LENGTH = 64;

    public CustomerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Customer ID must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Customer ID must be at most " + MAX_LENGTH + " characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
