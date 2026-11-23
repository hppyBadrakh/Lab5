package com.ecommerce.order.domain.model;

/** Value object: the number of units ordered for one SKU, from 1 to 999. */
public record Quantity(int value) {

    public static final int MAX = 999;

    public Quantity {
        if (value <= 0 || value > MAX) {
            throw new IllegalArgumentException("Quantity must be in range 1.." + MAX + ", got: " + value);
        }
    }

    /** Returns a new quantity. Throws if the sum leaves the allowed range. */
    public Quantity plus(Quantity other) {
        return new Quantity(value + other.value);
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
