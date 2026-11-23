package com.ecommerce.order.presentation;

import com.ecommerce.order.domain.Money;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String orderId,
        String customerId,
        OrderStatus status,
        List<Item> items,
        BigDecimal total,
        String currency,
        Instant createdAt,
        Instant updatedAt
) {

    public static OrderResponse fromDomain(Order order) {
        Money total = order.calculateTotal();
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getItems().stream().map(Item::fromDomain).toList(),
                total.amount(),
                total.currency(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public record Item(String productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {

        static Item fromDomain(OrderItem item) {
            return new Item(item.getProductId(), item.getProductName(), item.getUnitPrice().amount(),
                    item.getQuantity(), item.calculateSubtotal().amount());
        }
    }
}
