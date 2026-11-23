package com.ecommerce.order.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusDomainTest {

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @DisplayName("State machine: DRAFT -> PLACED -> (PAYMENT_PENDING ->) PAID, and CANCELLED from any non-final state")
    @CsvSource({
            "DRAFT,DRAFT,false", "DRAFT,PLACED,true", "DRAFT,PAYMENT_PENDING,false", "DRAFT,PAID,false", "DRAFT,CANCELLED,true",
            "PLACED,DRAFT,false", "PLACED,PLACED,false", "PLACED,PAYMENT_PENDING,true", "PLACED,PAID,true", "PLACED,CANCELLED,true",
            "PAYMENT_PENDING,DRAFT,false", "PAYMENT_PENDING,PLACED,false", "PAYMENT_PENDING,PAYMENT_PENDING,false",
            "PAYMENT_PENDING,PAID,true", "PAYMENT_PENDING,CANCELLED,true",
            "PAID,DRAFT,false", "PAID,PLACED,false", "PAID,PAYMENT_PENDING,false", "PAID,PAID,false", "PAID,CANCELLED,true",
            "CANCELLED,DRAFT,false", "CANCELLED,PLACED,false", "CANCELLED,PAYMENT_PENDING,false", "CANCELLED,PAID,false",
            "CANCELLED,CANCELLED,false"
    })
    void shouldMatchTransitionTable(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
