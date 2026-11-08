package com.ecommerce.order.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Order Aggregate Root - domain invariant tests")
class OrderAggregateTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final ShippingAddress ADDRESS = new ShippingAddress("Peace Ave 1", "Ulaanbaatar", "14200", "MN");
    private static final Sku KEYBOARD = new Sku("ELEC-1001");
    private static final Sku MOUSE = new Sku("ELEC-2002");

    private Order order;

    @BeforeEach
    void newDraftOrder() {
        order = new Order(OrderId.generate(), new CustomerId("CUST-1"), USD, ADDRESS);
    }

    @Test
    @DisplayName("Happy path: add items, place, total is correct")
    void shouldPlaceOrderSuccessfully() {
        order.addLineItem(KEYBOARD, Money.of("150.00", "USD"), new Quantity(2));

        order.placeOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.calculateTotal()).isEqualTo(Money.of("300.00", "USD"));
    }

    @Test
    @DisplayName("A new order starts as an empty DRAFT with a zero total")
    void shouldStartAsEmptyDraft() {
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
        assertThat(order.getItems()).isEmpty();
        assertThat(order.calculateTotal()).isEqualTo(Money.zero(USD));
        assertThat(order.getUpdatedAt()).isEqualTo(order.getCreatedAt());
    }

    @Test
    @DisplayName("Placing an empty order is rejected")
    void shouldRejectPlacingEmptyOrder() {
        assertThatThrownBy(order::placeOrder)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot place an order with zero line items");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
    }

    @Nested
    @DisplayName("Line items")
    class LineItems {

        @Test
        @DisplayName("Adding the same SKU again merges into one line with the summed quantity")
        void shouldMergeSameSku() {
            order.addLineItem(KEYBOARD, Money.of("10.00", "USD"), new Quantity(2));
            order.addLineItem(MOUSE, Money.of("5.00", "USD"), new Quantity(1));
            order.addLineItem(KEYBOARD, Money.of("10.00", "USD"), new Quantity(3));

            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getItems().getFirst().getQuantity()).isEqualTo(new Quantity(5));
            assertThat(order.calculateTotal()).isEqualTo(Money.of("55.00", "USD"));
        }

        @Test
        @DisplayName("The same SKU at a different price is rejected instead of silently repriced")
        void shouldRejectSameSkuAtDifferentPrice() {
            order.addLineItem(KEYBOARD, Money.of("10.00", "USD"), new Quantity(1));

            assertThatThrownBy(() -> order.addLineItem(KEYBOARD, Money.of("9.00", "USD"), new Quantity(1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already on the order");
        }

        @Test
        @DisplayName("Merging past 999 units is rejected and the line keeps its quantity")
        void shouldRejectMergedQuantityAboveLimit() {
            order.addLineItem(KEYBOARD, Money.of("1.00", "USD"), new Quantity(998));

            assertThatThrownBy(() -> order.addLineItem(KEYBOARD, Money.of("1.00", "USD"), new Quantity(2)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("999");
            assertThat(order.getItems().getFirst().getQuantity()).isEqualTo(new Quantity(998));
        }

        @Test
        @DisplayName("A line in another currency is rejected")
        void shouldRejectForeignCurrency() {
            assertThatThrownBy(() -> order.addLineItem(KEYBOARD, Money.of("10.00", "EUR"), new Quantity(1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency mismatch");
            assertThat(order.getItems()).isEmpty();
        }

        @Test
        @DisplayName("getItems is a read-only snapshot")
        void shouldProtectItems() {
            order.addLineItem(KEYBOARD, Money.of("10.00", "USD"), new Quantity(1));

            assertThatThrownBy(() -> order.getItems().clear()).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Null arguments are rejected")
        void shouldRejectNulls() {
            Money price = Money.of("1.00", "USD");
            Quantity one = new Quantity(1);

            assertThatThrownBy(() -> order.addLineItem(null, price, one)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> order.addLineItem(KEYBOARD, null, one)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> order.addLineItem(KEYBOARD, price, null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("10,000 order limit")
    class OrderLimit {

        @Test
        @DisplayName("A total of exactly 10,000.00 is allowed")
        void shouldAllowExactlyTheLimit() {
            order.addLineItem(KEYBOARD, Money.of("2500.00", "USD"), new Quantity(4));

            assertThat(order.calculateTotal()).isEqualTo(Money.of("10000.00", "USD"));
        }

        @Test
        @DisplayName("Going over the limit is rejected and leaves the order unchanged")
        void shouldRejectAboveLimitAtomically() {
            order.addLineItem(KEYBOARD, Money.of("9999.99", "USD"), new Quantity(1));

            assertThatThrownBy(() -> order.addLineItem(MOUSE, Money.of("0.02", "USD"), new Quantity(1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("would exceed the limit of 10000.00 USD");
            assertThat(order.getItems()).hasSize(1);
            assertThat(order.calculateTotal()).isEqualTo(Money.of("9999.99", "USD"));
        }

        @Test
        @DisplayName("Merging into an existing line cannot sneak past the limit either")
        void shouldRejectMergeAboveLimit() {
            order.addLineItem(KEYBOARD, Money.of("5000.00", "USD"), new Quantity(2));

            assertThatThrownBy(() -> order.addLineItem(KEYBOARD, Money.of("5000.00", "USD"), new Quantity(1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(order.getItems().getFirst().getQuantity()).isEqualTo(new Quantity(2));
        }

        @Test
        @DisplayName("The limit is applied in the order currency")
        void shouldApplyLimitInOrderCurrency() {
            Order eurOrder = new Order(OrderId.generate(), new CustomerId("CUST-1"), Currency.getInstance("EUR"), ADDRESS);

            assertThatThrownBy(() -> eurOrder.addLineItem(KEYBOARD, Money.of("10000.01", "EUR"), new Quantity(1)))
                    .hasMessageContaining("10000.00 EUR");
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @BeforeEach
        void addOneLine() {
            order.addLineItem(KEYBOARD, Money.of("10.00", "USD"), new Quantity(1));
        }

        @Test
        @DisplayName("DRAFT -> PLACED -> PAID -> CANCELLED")
        void shouldWalkTheLifecycle() {
            order.placeOrder();
            order.markAsPaid();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

            order.cancel();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("Items cannot be added once the order is placed")
        void shouldRejectAddingAfterPlacing() {
            order.placeOrder();

            assertThatThrownBy(() -> order.addLineItem(MOUSE, Money.of("1.00", "USD"), new Quantity(1)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot add line items when order is PLACED");
        }

        @Test
        @DisplayName("Items cannot be added to a cancelled order")
        void shouldRejectAddingToCancelledOrder() {
            order.cancel();

            assertThatThrownBy(() -> order.addLineItem(MOUSE, Money.of("1.00", "USD"), new Quantity(1)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CANCELLED");
        }

        @Test
        @DisplayName("A draft cannot be paid, a cancelled order cannot be paid, an order cannot be placed twice")
        void shouldRejectIllegalTransitions() {
            assertThatThrownBy(order::markAsPaid)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DRAFT to PAID");

            order.placeOrder();
            assertThatThrownBy(order::placeOrder).isInstanceOf(IllegalStateException.class);

            order.cancel();
            assertThatThrownBy(order::markAsPaid)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CANCELLED to PAID");
            assertThatThrownBy(order::cancel).isInstanceOf(IllegalStateException.class);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("Every change moves updatedAt forward on the injected clock")
        void shouldTrackUpdateTime() {
            MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
            Order timed = new Order(OrderId.generate(), new CustomerId("CUST-1"), USD, ADDRESS, clock);
            timed.addLineItem(KEYBOARD, Money.of("1.00", "USD"), new Quantity(1));

            clock.now = Instant.parse("2026-01-01T00:05:00Z");
            timed.placeOrder();

            assertThat(timed.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
            assertThat(timed.getUpdatedAt()).isEqualTo(Instant.parse("2026-01-01T00:05:00Z"));
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
