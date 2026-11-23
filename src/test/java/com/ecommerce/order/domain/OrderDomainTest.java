package com.ecommerce.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderDomainTest {

    private static OrderItem item(String productId, double price, String currency, int quantity) {
        return new OrderItem(productId, "Item " + productId, Money.of(price, currency), quantity);
    }

    @Test
    @DisplayName("A new order is PENDING with a generated id")
    void shouldStartPending() {
        Order order = new Order("CUST-1", List.of(item("P1", 10, "USD", 1)));

        assertThat(order.getId()).isNotBlank();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getUpdatedAt()).isEqualTo(order.getCreatedAt());
    }

    @Test
    @DisplayName("calculateTotal sums every item subtotal")
    void shouldCalculateTotal() {
        Order order = new Order("CUST-1", List.of(item("P1", 19.99, "EUR", 3), item("P2", 5.01, "EUR", 2)));

        assertThat(order.calculateTotal().amount()).isEqualByComparingTo("69.99");
        assertThat(order.calculateTotal().currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("An order without items is rejected")
    void shouldRejectNoItems() {
        assertThatThrownBy(() -> new Order("CUST-1", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Order("CUST-1", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("An order mixing currencies is rejected at construction")
    void shouldRejectMixedCurrencies() {
        List<OrderItem> mixed = List.of(item("P1", 10, "USD", 1), item("P2", 10, "EUR", 1));

        assertThatThrownBy(() -> new Order("CUST-1", mixed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same currency");
    }

    @Test
    @DisplayName("Paying then cancelling walks the state machine and updates the timestamp")
    void shouldTransition() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Order order = new Order("ORD-1", "CUST-1", List.of(item("P1", 10, "USD", 1)), OrderStatus.PENDING, createdAt);

        order.markAsPaid();
        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getUpdatedAt()).isAfter(createdAt);
    }

    @Test
    @DisplayName("An illegal transition throws and leaves the status unchanged")
    void shouldRejectIllegalTransition() {
        Order order = new Order("ORD-1", "CUST-1", List.of(item("P1", 10, "USD", 1)),
                OrderStatus.DELIVERED, Instant.now());

        assertThatThrownBy(order::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DELIVERED to CANCELLED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("getItems does not expose the internal list")
    void shouldProtectItems() {
        Order order = new Order("CUST-1", List.of(item("P1", 10, "USD", 1)));

        assertThatThrownBy(() -> order.getItems().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("OrderItem rejects blank ids and names, negative prices and non-positive quantities")
    void shouldValidateItem() {
        Money price = Money.of(1, "USD");

        assertThatThrownBy(() -> new OrderItem(" ", "Name", price, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderItem("P1", " ", price, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderItem("P1", "Name", Money.of(-1, "USD"), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderItem("P1", "Name", price, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
