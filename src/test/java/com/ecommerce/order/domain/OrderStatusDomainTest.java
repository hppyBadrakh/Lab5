package com.ecommerce.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusDomainTest {

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @DisplayName("State machine allows exactly the documented transitions")
    @CsvSource({
            "PENDING,PENDING,false", "PENDING,PAID,true", "PENDING,SHIPPED,false",
            "PENDING,DELIVERED,false", "PENDING,CANCELLED,true",
            "PAID,PENDING,false", "PAID,PAID,false", "PAID,SHIPPED,true",
            "PAID,DELIVERED,false", "PAID,CANCELLED,true",
            "SHIPPED,PENDING,false", "SHIPPED,PAID,false", "SHIPPED,SHIPPED,false",
            "SHIPPED,DELIVERED,true", "SHIPPED,CANCELLED,false",
            "DELIVERED,PENDING,false", "DELIVERED,PAID,false", "DELIVERED,SHIPPED,false",
            "DELIVERED,DELIVERED,false", "DELIVERED,CANCELLED,false",
            "CANCELLED,PENDING,false", "CANCELLED,PAID,false", "CANCELLED,SHIPPED,false",
            "CANCELLED,DELIVERED,false", "CANCELLED,CANCELLED,false"
    })
    void shouldMatchTransitionTable(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
