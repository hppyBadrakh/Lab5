package com.ecommerce.order.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Money - immutable value object")
class MoneyValueObjectTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Test
    @DisplayName("0.1 x 3 is exactly 0.30, unlike double arithmetic")
    void shouldAvoidFloatingPointDrift() {
        assertThat(0.1 * 3).isNotEqualTo(0.3);
        assertThat(Money.of("0.10", "USD").multiply(3)).isEqualTo(Money.of("0.30", "USD"));
    }

    @Test
    @DisplayName("Amounts are normalized to the currency's minor units with banker's rounding")
    void shouldNormalizeScale() {
        assertThat(Money.of("10", "USD").amount()).isEqualByComparingTo("10.00");
        assertThat(Money.of("10", "USD").amount().scale()).isEqualTo(2);
        assertThat(Money.of("10.005", "USD").amount()).isEqualByComparingTo("10.00");
        assertThat(Money.of("10.015", "USD").amount()).isEqualByComparingTo("10.02");
        assertThat(Money.of("1500", "JPY").amount().scale()).isZero();
    }

    @Test
    @DisplayName("Equal amount and currency means equal values, regardless of input scale")
    void shouldHaveValueEquality() {
        assertThat(Money.of("5", "USD")).isEqualTo(Money.of("5.00", "USD")).hasSameHashCodeAs(Money.of("5.0", "USD"));
        assertThat(Money.of("5", "USD")).isNotEqualTo(Money.of("5", "EUR"));
    }

    @Test
    @DisplayName("add sums amounts in the same currency and returns a new instance")
    void shouldAddSameCurrency() {
        Money a = Money.of("1.10", "USD");
        Money b = Money.of("2.20", "USD");

        assertThat(a.add(b)).isEqualTo(Money.of("3.30", "USD"));
        assertThat(a).isEqualTo(Money.of("1.10", "USD"));
    }

    @Test
    @DisplayName("Mixing currencies is refused")
    void shouldRejectCurrencyMismatch() {
        Money usd = Money.of("1", "USD");
        Money eur = Money.of("1", "EUR");

        assertThatThrownBy(() -> usd.add(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
        assertThatThrownBy(() -> usd.isGreaterThan(eur)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("multiply works exactly and rejects negative factors")
    void shouldMultiply() {
        assertThat(Money.of("19.99", "USD").multiply(3)).isEqualTo(Money.of("59.97", "USD"));
        assertThat(Money.of("200.00", "EUR").multiply(new BigDecimal("1.08"))).isEqualTo(Money.of("216.00", "EUR"));
        assertThatThrownBy(() -> Money.of("1", "USD").multiply(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of("1", "USD").multiply(new BigDecimal("-0.5")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isGreaterThan compares amounts")
    void shouldCompare() {
        assertThat(Money.of("10000.01", "USD").isGreaterThan(Money.of("10000", "USD"))).isTrue();
        assertThat(Money.of("10000.00", "USD").isGreaterThan(Money.of("10000", "USD"))).isFalse();
    }

    @Test
    @DisplayName("Negative amounts, null parts and unknown currency codes are rejected")
    void shouldRejectInvalidInput() {
        assertThatThrownBy(() -> Money.of("-0.01", "USD")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of("1", "usd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of("1", "ABC")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(null, USD)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("zero and toString")
    void shouldCreateZeroAndPrint() {
        assertThat(Money.zero(USD).amount()).isEqualByComparingTo("0");
        assertThat(Money.of("12.5", "USD")).hasToString("12.50 USD");
        assertThat(Money.of("12.5", "USD").currencyCode()).isEqualTo("USD");
    }
}
