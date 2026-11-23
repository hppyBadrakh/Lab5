package com.ecommerce.order.domain.model;

import java.util.Objects;

/**
 * Value object: what the payment side answered for one charge attempt.
 *
 * @param reference gateway transaction id for {@code SUCCESS}, local intent token for {@code DEFERRED_PENDING}
 * @param orderId   the order that was charged
 * @param status    outcome of the attempt
 * @param message   human-readable explanation
 */
public record PaymentResult(String reference, OrderId orderId, PaymentStatus status, String message) {

    public enum PaymentStatus {
        /** The gateway charged the customer. */
        SUCCESS,
        /** The gateway was unavailable; the intent was stored and will be settled later. */
        DEFERRED_PENDING,
        /** The gateway answered and refused the charge (for example insufficient funds). */
        DECLINED
    }

    public PaymentResult {
        Objects.requireNonNull(orderId, "Order id must not be null");
        Objects.requireNonNull(status, "Payment status must not be null");
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Payment reference must not be blank");
        }
    }
}
