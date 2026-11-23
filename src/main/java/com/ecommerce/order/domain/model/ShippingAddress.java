package com.ecommerce.order.domain.model;

import java.util.regex.Pattern;

/** Value object: where an order is delivered. {@code countryCode} is ISO 3166-1 alpha-2, for example {@code MN}. */
public record ShippingAddress(String street, String city, String postalCode, String countryCode) {

    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");

    public ShippingAddress {
        requireText(street, "Street");
        requireText(city, "City");
        requireText(postalCode, "Postal code");
        if (countryCode == null || !COUNTRY.matcher(countryCode).matches()) {
            throw new IllegalArgumentException("Country code must be two uppercase letters, got: " + countryCode);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
