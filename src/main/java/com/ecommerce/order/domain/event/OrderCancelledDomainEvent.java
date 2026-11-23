package com.ecommerce.order.domain.event;

import com.ecommerce.order.domain.model.OrderId;
import com.ecommerce.order.domain.model.OrderStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised by {@code Order.cancel()}. {@code previousStatus} tells Payment whether a refund is needed. */
public record OrderCancelledDomainEvent(
        UUID eventId,
        OrderId orderId,
        OrderStatus previousStatus,
        Instant occurredOn
) implements DomainEvent {

    public OrderCancelledDomainEvent {
        Objects.requireNonNull(eventId, "Event ID must not be null");
        Objects.requireNonNull(orderId, "Order ID must not be null");
        Objects.requireNonNull(previousStatus, "Previous status must not be null");
        Objects.requireNonNull(occurredOn, "Occurrence time must not be null");
    }
}
