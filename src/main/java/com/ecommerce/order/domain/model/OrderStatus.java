package com.ecommerce.order.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Value object: the order lifecycle.
 * <pre>
 * DRAFT --place--> PLACED --pay--------------------------> PAID
 *   |                |  \                                 ^   |
 *   |                |   +--gateway down--> PAYMENT_PENDING  |
 *   |                |                          |   (retry pay)
 *   +----cancel------+---------cancel-----------+-----cancel-+--> CANCELLED (final)
 * </pre>
 * PAYMENT_PENDING means the payment intent was accepted while the gateway was unavailable
 * and still has to be settled (ADR-0008).
 */
public enum OrderStatus {
    DRAFT,
    PLACED,
    PAYMENT_PENDING,
    PAID,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus next) {
        return allowedNext().contains(next);
    }

    private Set<OrderStatus> allowedNext() {
        return switch (this) {
            case DRAFT -> EnumSet.of(PLACED, CANCELLED);
            case PLACED -> EnumSet.of(PAYMENT_PENDING, PAID, CANCELLED);
            case PAYMENT_PENDING -> EnumSet.of(PAID, CANCELLED);
            case PAID -> EnumSet.of(CANCELLED);
            case CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }
}
