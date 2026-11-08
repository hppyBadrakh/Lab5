package com.ecommerce.order.domain.model;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root of the Order Processing context and its consistency boundary.
 * Every change to line items or status goes through this class, which enforces:
 * <ol>
 *   <li>line items can be added only while the order is {@link OrderStatus#DRAFT};</li>
 *   <li>every line item uses the order currency;</li>
 *   <li>the order total never exceeds {@link #MAX_TOTAL_AMOUNT} in the order currency;</li>
 *   <li>an order cannot be placed without line items;</li>
 *   <li>status changes follow {@link OrderStatus#canTransitionTo(OrderStatus)}.</li>
 * </ol>
 * A rejected command throws and leaves the aggregate unchanged.
 */
public class Order {

    public static final BigDecimal MAX_TOTAL_AMOUNT = new BigDecimal("10000.00");

    private final OrderId id;
    private final CustomerId customerId;
    private final Currency currency;
    private final ShippingAddress shippingAddress;
    private final List<OrderItem> items = new ArrayList<>();
    private final Clock clock;
    private final Instant createdAt;
    private OrderStatus status = OrderStatus.DRAFT;
    private Instant updatedAt;

    public Order(OrderId id, CustomerId customerId, Currency currency, ShippingAddress shippingAddress) {
        this(id, customerId, currency, shippingAddress, Clock.systemUTC());
    }

    public Order(OrderId id, CustomerId customerId, Currency currency, ShippingAddress shippingAddress, Clock clock) {
        this.id = Objects.requireNonNull(id, "Order ID must not be null");
        this.customerId = Objects.requireNonNull(customerId, "Customer ID must not be null");
        this.currency = Objects.requireNonNull(currency, "Currency must not be null");
        this.shippingAddress = Objects.requireNonNull(shippingAddress, "Shipping address must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.createdAt = clock.instant();
        this.updatedAt = createdAt;
    }

    /**
     * Adds a line, or increases the quantity of the line that already has this SKU.
     * All checks run before anything changes, so a rejected call leaves the order as it was.
     */
    public void addLineItem(Sku sku, Money unitPrice, Quantity quantity) {
        Objects.requireNonNull(sku, "SKU must not be null");
        Objects.requireNonNull(unitPrice, "Unit price must not be null");
        Objects.requireNonNull(quantity, "Quantity must not be null");
        assertStatus(OrderStatus.DRAFT, "Cannot add line items when order is " + status);
        if (!unitPrice.currency().equals(currency)) {
            throw new IllegalArgumentException("Currency mismatch on line item: order is in " + currency
                    + " but the line item is in " + unitPrice.currency());
        }

        Optional<OrderItem> existing = findLine(sku);
        existing.ifPresent(line -> assertMergeable(line, unitPrice, quantity));
        Money newTotal = calculateTotal().add(unitPrice.multiply(quantity.value()));
        if (newTotal.isGreaterThan(maxTotal())) {
            throw new IllegalArgumentException("Order total " + newTotal + " would exceed the limit of " + maxTotal());
        }

        existing.ifPresentOrElse(
                line -> line.increaseQuantityBy(quantity),
                () -> items.add(new OrderItem(sku, unitPrice, quantity)));
        touch();
    }

    public void placeOrder() {
        assertStatus(OrderStatus.DRAFT, "Order must be in DRAFT state to place, but is " + status);
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot place an order with zero line items");
        }
        transitionTo(OrderStatus.PLACED);
    }

    public void markAsPaid() {
        transitionTo(OrderStatus.PAID);
    }

    public void cancel() {
        transitionTo(OrderStatus.CANCELLED);
    }

    public Money calculateTotal() {
        Money total = Money.zero(currency);
        for (OrderItem item : items) {
            total = total.add(item.calculateSubtotal());
        }
        return total;
    }

    public OrderId getId() {
        return id;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public Currency getCurrency() {
        return currency;
    }

    public ShippingAddress getShippingAddress() {
        return shippingAddress;
    }

    /** A read-only snapshot. Callers cannot add or remove lines through it. */
    public List<OrderItem> getItems() {
        return List.copyOf(items);
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

    private void transitionTo(OrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Illegal state transition from " + status + " to " + next);
        }
        status = next;
        touch();
    }

    private void assertStatus(OrderStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    private static void assertMergeable(OrderItem line, Money unitPrice, Quantity extra) {
        if (!line.getUnitPrice().equals(unitPrice)) {
            throw new IllegalArgumentException("SKU " + line.getSku() + " is already on the order at "
                    + line.getUnitPrice() + ", not " + unitPrice);
        }
        if (line.getQuantity().value() + extra.value() > Quantity.MAX) {
            throw new IllegalArgumentException("Quantity for SKU " + line.getSku() + " would exceed " + Quantity.MAX);
        }
    }

    private Optional<OrderItem> findLine(Sku sku) {
        return items.stream().filter(item -> item.getSku().equals(sku)).findFirst();
    }

    private Money maxTotal() {
        return Money.of(MAX_TOTAL_AMOUNT, currency);
    }

    private void touch() {
        updatedAt = clock.instant();
    }
}
