package com.ecommerce.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyDomainTest {

    @Test
    @DisplayName("Amounts are normalized to two decimals with HALF_UP rounding")
    void shouldRoundToTwoDecimals() {
        assertThat(Money.of(10.005, "USD").amount()).isEqualByComparingTo("10.01");
        assertThat(Money.of(10, "USD").amount().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("add sums amounts in the same currency, ignoring case")
    void shouldAddSameCurrency() {
        assertThat(Money.of(1.10, "USD").add(Money.of(2.20, "usd")).amount()).isEqualByComparingTo("3.30");
    }

    @Test
    @DisplayName("add rejects a different currency")
    void shouldRejectCurrencyMismatch() {
        assertThatThrownBy(() -> Money.of(1, "USD").add(Money.of(1, "EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency mismatch");
    }

    @Test
    @DisplayName("multiply avoids binary floating point drift")
    void shouldMultiplyExactly() {
        assertThat(Money.of(19.99, "USD").multiply(3).amount()).isEqualByComparingTo(new BigDecimal("59.97"));
    }

    @Test
    @DisplayName("Null amount and null currency are rejected")
    void shouldRejectNulls() {
        assertThatThrownBy(() -> new Money(null, "USD")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
    }
}
