package com.ecommerce.order.infrastructure.adapter.in.web;

import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        String orderId,
        String customerId,
        OrderStatus status,
        List<Item> items,
        BigDecimal total,
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
                order.getItems().stream().map(Item::fromDomain).toList(),
                total.amount(),
                total.currencyCode(),
                ShippingAddressDto.fromDomain(order.getShippingAddress()),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public record Item(UUID lineId, String sku, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {

        static Item fromDomain(OrderItem item) {
            return new Item(item.getLineId(), item.getSku().value(), item.getUnitPrice().amount(),
                    item.getQuantity().value(), item.calculateSubtotal().amount());
        }
    }
}
