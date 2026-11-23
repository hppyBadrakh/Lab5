package com.ecommerce.order.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderItem - entity with its own identity")
class OrderItemDomainTest {

    private static final Sku SKU = new Sku("ELEC-1001");
    private static final Money PRICE = Money.of("19.99", "USD");

    @Test
    @DisplayName("Subtotal is unit price x quantity")
    void shouldCalculateSubtotal() {
        OrderItem item = new OrderItem(SKU, PRICE, new Quantity(3));

        assertThat(item.calculateSubtotal()).isEqualTo(Money.of("59.97", "USD"));
    }

    @Test
    @DisplayName("Two lines with identical data are still different entities")
    void shouldUseIdentityEquality() {
        OrderItem first = new OrderItem(SKU, PRICE, new Quantity(1));
        OrderItem second = new OrderItem(SKU, PRICE, new Quantity(1));

        assertThat(first).isNotEqualTo(second).isEqualTo(first);
        assertThat(first.getLineId()).isNotEqualTo(second.getLineId());
    }

    @Test
    @DisplayName("Increasing the quantity keeps the identity and respects the 999 limit")
    void shouldIncreaseQuantity() {
        OrderItem item = new OrderItem(SKU, PRICE, new Quantity(2));
        var lineId = item.getLineId();

        item.increaseQuantityBy(new Quantity(3));

        assertThat(item.getQuantity()).isEqualTo(new Quantity(5));
        assertThat(item.getLineId()).isEqualTo(lineId);
        assertThatThrownBy(() -> item.increaseQuantityBy(new Quantity(995)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(item.getQuantity()).isEqualTo(new Quantity(5));
    }

    @Test
    @DisplayName("Null parts are rejected")
    void shouldRejectNulls() {
        Quantity one = new Quantity(1);

        assertThatThrownBy(() -> new OrderItem(null, PRICE, one)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OrderItem(SKU, null, one)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OrderItem(SKU, PRICE, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("No public setters and no public mutators: only the aggregate may change a line")
    void shouldExposeNoPublicMutators() {
        assertThat(Arrays.stream(OrderItem.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName))
                .noneMatch(name -> name.startsWith("set") || name.startsWith("increase") || name.startsWith("update"));
        assertThat(Arrays.stream(OrderItem.class.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers()))).isTrue();
    }
}
