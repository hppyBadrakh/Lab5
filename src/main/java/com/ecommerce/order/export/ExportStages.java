package com.ecommerce.order.export;

import com.ecommerce.order.domain.Money;

import java.time.Instant;

/** One type per pipeline stage, so filters cannot be assembled in the wrong order. */
public final class ExportStages {

    private ExportStages() {
    }

    public record Sanitized(String orderId, String customerId, String status, Money amount, Instant createdAt) {
    }

    public record Normalized(Sanitized order, Money amountUsd) {
    }

    public record Taxed(Normalized order, Money taxUsd) {
    }
}
