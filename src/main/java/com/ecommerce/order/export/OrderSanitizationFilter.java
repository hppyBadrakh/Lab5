package com.ecommerce.order.export;

import com.ecommerce.order.domain.Order;

/** Rejects orders that cannot be audited and neutralizes text that spreadsheets would run as a formula. */
public class OrderSanitizationFilter implements Filter<Order, ExportStages.Sanitized> {

    @Override
    public ExportStages.Sanitized process(Order order) {
        return new ExportStages.Sanitized(
                sanitize(order.getId()),
                sanitize(order.getCustomerId()),
                order.getStatus().name(),
                order.calculateTotal(),
                order.getCreatedAt());
    }

    private static String sanitize(String value) {
        String cleaned = value.replaceAll("\\p{Cntrl}", " ").strip();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Order export requires non-blank identifiers");
        }
        return "=+-@".indexOf(cleaned.charAt(0)) >= 0 ? "'" + cleaned : cleaned;
    }
}
