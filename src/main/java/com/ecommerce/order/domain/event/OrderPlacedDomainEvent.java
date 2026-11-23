package com.ecommerce.order.domain.event;

import com.ecommerce.order.domain.model.CustomerId;
import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.OrderId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised by {@code Order.placeOrder()}. Inventory reserves stock and the audit log records it in reaction. */
public record OrderPlacedDomainEvent(
        UUID eventId,
        OrderId orderId,
        CustomerId customerId,
        Money orderTotal,
        Instant occurredOn
) implements DomainEvent {

    public OrderPlacedDomainEvent {
        Objects.requireNonNull(eventId, "Event ID must not be null");
        Objects.requireNonNull(orderId, "Order ID must not be null");
        Objects.requireNonNull(customerId, "Customer ID must not be null");
        Objects.requireNonNull(orderTotal, "Order total must not be null");
        Objects.requireNonNull(occurredOn, "Occurrence time must not be null");
    }
}
