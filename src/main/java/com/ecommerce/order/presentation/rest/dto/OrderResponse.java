package com.ecommerce.order.presentation.rest.dto;

import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Complete representation of an order resource")
public record OrderResponse(
        @Schema(description = "Server-generated order id", example = "0b9f6c1e-6f0a-4c58-9a59-3f1f3f1c2b7d")
        String orderId,

        @Schema(example = "CUST-1")
        String customerId,

        @Schema(description = "Lifecycle state: PLACED -> PAID, and CANCELLED from either")
        OrderStatus status,

        List<OrderItemResponse> items,

        @Schema(description = "Sum of all line subtotals", example = "51.00")
        BigDecimal total,

        @Schema(description = "Three-letter ISO 4217 currency code of every amount in this order", example = "USD")
        String currency,

        ShippingAddressDto shippingAddress,

        Instant createdAt,

        Instant updatedAt
) {

    public static OrderResponse fromDomain(Order order) {
        Money total = order.calculateTotal();
        return new OrderResponse(
                order.getId().value(),
                order.getCustomerId().value(),
                order.getStatus(),
                order.getItems().stream().map(OrderItemResponse::fromDomain).toList(),
                total.amount(),
                total.currencyCode(),
                ShippingAddressDto.fromDomain(order.getShippingAddress()),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
