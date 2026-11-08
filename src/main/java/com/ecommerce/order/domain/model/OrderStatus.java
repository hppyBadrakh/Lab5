package com.ecommerce.order.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Value object: the order lifecycle.
 * <pre>
 * DRAFT --place--> PLACED --pay--> PAID
 *   |                |              |
 *   +----cancel------+----cancel----+--> CANCELLED (final)
 * </pre>
 */
public enum OrderStatus {
    DRAFT,
    PLACED,
    PAID,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus next) {
        return allowedNext().contains(next);
    }

    private Set<OrderStatus> allowedNext() {
        return switch (this) {
            case DRAFT -> EnumSet.of(PLACED, CANCELLED);
            case PLACED -> EnumSet.of(PAID, CANCELLED);
            case PAID -> EnumSet.of(CANCELLED);
            case CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }
}
