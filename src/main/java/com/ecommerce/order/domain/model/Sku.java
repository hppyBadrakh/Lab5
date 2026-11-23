package com.ecommerce.order.domain.model;

import java.util.regex.Pattern;

/** Value object: Stock Keeping Unit shared with the Inventory context, for example {@code ELEC-1001}. */
public record Sku(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z0-9]{3,12}-[A-Z0-9]{2,6}$");

    public Sku {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid SKU format: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
