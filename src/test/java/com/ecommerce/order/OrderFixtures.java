package com.ecommerce.order;

import com.ecommerce.order.domain.model.CustomerId;
import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderId;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.model.Quantity;
import com.ecommerce.order.domain.model.ShippingAddress;
import com.ecommerce.order.domain.model.Sku;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;

/** Builds orders in a given status the only legal way: through the aggregate's own commands. */
public final class OrderFixtures {

    public static final ShippingAddress ADDRESS = new ShippingAddress("Peace Ave 1", "Ulaanbaatar", "14200", "MN");
    public static final Instant CREATED_AT = Instant.parse("2026-11-01T00:00:00Z");
    public static final Sku SKU = new Sku("ELEC-1001");

    private OrderFixtures() {
    }

    public static Order order(String id, String customerId, String unitPrice, String currency, int quantity,
                              OrderStatus status, Instant createdAt) {
        Order order = new Order(new OrderId(id), new CustomerId(customerId), Currency.getInstance(currency), ADDRESS,
                Clock.fixed(createdAt, ZoneOffset.UTC));
        order.addLineItem(SKU, Money.of(unitPrice, currency), new Quantity(quantity));
        switch (status) {
            case DRAFT -> { }
            case PLACED -> order.placeOrder();
            case PAID -> {
                order.placeOrder();
                order.markAsPaid();
            }
            case CANCELLED -> {
                order.placeOrder();
                order.cancel();
            }
        }
        return order;
    }

    public static Order order(String id, OrderStatus status) {
        return order(id, "CUST-1", "100.00", "USD", 2, status, CREATED_AT);
    }
}
