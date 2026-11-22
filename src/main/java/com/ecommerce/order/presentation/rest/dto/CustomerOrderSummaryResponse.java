package com.ecommerce.order.presentation.rest.dto;

import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Short view of an order for a customer's order history. Fetch the order itself for the lines.")
public record CustomerOrderSummaryResponse(
        @Schema(example = "0b9f6c1e-6f0a-4c58-9a59-3f1f3f1c2b7d")
        String orderId,

        OrderStatus status,

        @Schema(example = "51.00")
        BigDecimal total,

        @Schema(description = "Three-letter ISO 4217 currency code", example = "USD")
        String currency,

        @Schema(description = "Number of distinct lines, not units", example = "1")
        int itemCount,

        Instant createdAt
) {

    public static CustomerOrderSummaryResponse fromDomain(Order order) {
        Money total = order.calculateTotal();
        return new CustomerOrderSummaryResponse(order.getId().value(), order.getStatus(), total.amount(),
                total.currencyCode(), order.getItems().size(), order.getCreatedAt());
    }
}
