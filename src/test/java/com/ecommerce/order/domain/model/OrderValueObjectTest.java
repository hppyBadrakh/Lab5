package com.ecommerce.order.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Sku, Quantity, OrderId, CustomerId, ShippingAddress - value objects")
class OrderValueObjectTest {

    @ParameterizedTest
    @ValueSource(strings = {"ELEC-1001", "ABC-12", "ABCDEFGHIJKL-ABCDEF"})
    @DisplayName("Sku accepts CATEGORY-CODE format")
    void shouldAcceptValidSku(String value) {
        assertThat(new Sku(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"P1", "elec-1001", "AB-12", "ELEC-1", "ELEC1001", "ELEC-1001-X", "ABCDEFGHIJKLM-12"})
    @DisplayName("Sku rejects anything else")
    void shouldRejectInvalidSku(String value) {
        assertThatThrownBy(() -> new Sku(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid SKU format");
    }

    @Test
    @DisplayName("Quantity is limited to 1..999")
    void shouldBoundQuantity() {
        assertThat(new Quantity(1).value()).isEqualTo(1);
        assertThat(new Quantity(999).value()).isEqualTo(999);
        assertThatThrownBy(() -> new Quantity(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Quantity(-5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Quantity(1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1..999");
    }

    @Test
    @DisplayName("Quantity.plus returns a new value and keeps the range")
    void shouldAddQuantities() {
        Quantity two = new Quantity(2);

        assertThat(two.plus(new Quantity(3))).isEqualTo(new Quantity(5));
        assertThat(two).isEqualTo(new Quantity(2));
        assertThatThrownBy(() -> new Quantity(998).plus(two)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OrderId and CustomerId reject blank or oversized values and compare by value")
    void shouldValidateIdentifiers() {
        assertThat(new OrderId("ORD-1")).isEqualTo(new OrderId("ORD-1"));
        assertThat(OrderId.generate()).isNotEqualTo(OrderId.generate());
        assertThat(new CustomerId("CUST-1")).hasToString("CUST-1");

        assertThatThrownBy(() -> new OrderId(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderId("x".repeat(65))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CustomerId("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CustomerId("x".repeat(65))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ShippingAddress requires every part and an ISO country code")
    void shouldValidateAddress() {
        ShippingAddress address = new ShippingAddress("Peace Ave 1", "Ulaanbaatar", "14200", "MN");

        assertThat(address).isEqualTo(new ShippingAddress("Peace Ave 1", "Ulaanbaatar", "14200", "MN"));
        assertThatThrownBy(() -> new ShippingAddress(" ", "Ulaanbaatar", "14200", "MN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShippingAddress("Peace Ave 1", null, "14200", "MN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShippingAddress("Peace Ave 1", "Ulaanbaatar", "", "MN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShippingAddress("Peace Ave 1", "Ulaanbaatar", "14200", "mn"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
