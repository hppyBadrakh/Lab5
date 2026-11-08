package com.ecommerce.order.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Entity: one line of an {@link Order}. Identity is the line id, not the field values,
 * so two lines with equal data are still different lines.
 * Only the aggregate creates lines and changes their quantity: the constructor and
 * {@link #increaseQuantityBy(Quantity)} are package-private, and there are no setters.
 */
public class OrderItem {

    private final UUID lineId;
    private final Sku sku;
    private final Money unitPrice;
    private Quantity quantity;

    OrderItem(Sku sku, Money unitPrice, Quantity quantity) {
        this.lineId = UUID.randomUUID();
        this.sku = Objects.requireNonNull(sku, "SKU must not be null");
        this.unitPrice = Objects.requireNonNull(unitPrice, "Unit price must not be null");
        this.quantity = Objects.requireNonNull(quantity, "Quantity must not be null");
    }

    public Money calculateSubtotal() {
        return unitPrice.multiply(quantity.value());
    }

    /** The subtotal this line would have after adding {@code extra}, without changing the line. */
    Money subtotalAfterAdding(Quantity extra) {
        return unitPrice.multiply(quantity.plus(extra).value());
    }

    void increaseQuantityBy(Quantity extra) {
        this.quantity = quantity.plus(extra);
    }

    public UUID getLineId() {
        return lineId;
    }

    public Sku getSku() {
        return sku;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof OrderItem item && lineId.equals(item.lineId);
    }

    @Override
    public int hashCode() {
        return lineId.hashCode();
    }
}
