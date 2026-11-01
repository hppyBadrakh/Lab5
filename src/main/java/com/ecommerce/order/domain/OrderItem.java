package com.ecommerce.order.domain;

import java.util.Objects;

public class OrderItem {

    private final String productId;
    private final String productName;
    private final Money unitPrice;
    private final int quantity;

    public OrderItem(String productId, String productName, Money unitPrice, int quantity) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Product ID required");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("Product name required");
        }
        this.unitPrice = Objects.requireNonNull(unitPrice, "Unit price must not be null");
        if (unitPrice.amount().signum() < 0) {
            throw new IllegalArgumentException("Unit price must not be negative");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
    }

    public Money calculateSubtotal() {
        return unitPrice.multiply(quantity);
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }
}
