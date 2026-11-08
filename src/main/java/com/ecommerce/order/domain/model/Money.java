package com.ecommerce.order.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Value object: a non-negative amount in one ISO-4217 currency.
 * Amounts are {@link BigDecimal}, so there is no binary floating point drift,
 * and arithmetic across currencies is refused instead of silently mixed.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money amount must not be negative: " + amount.toPlainString());
        }
        amount = amount.setScale(fractionDigits(currency), RoundingMode.HALF_EVEN);
    }

    /** Parses a decimal string such as {@code "19.99"}. There is deliberately no {@code double} factory. */
    public static Money of(String amount, String currencyCode) {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(currencyCode, "Currency must not be null");
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money multiply(int factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("Factor cannot be negative: " + factor);
        }
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "Factor must not be null");
        if (factor.signum() < 0) {
            throw new IllegalArgumentException("Factor cannot be negative: " + factor.toPlainString());
        }
        return new Money(amount.multiply(factor), currency);
    }

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }

    private void assertSameCurrency(Money other) {
        Objects.requireNonNull(other, "Other amount must not be null");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    /** Currencies without minor units (JPY) keep 0 decimals. Pseudo-currencies (XXX) fall back to 2. */
    private static int fractionDigits(Currency currency) {
        int digits = currency.getDefaultFractionDigits();
        return digits < 0 ? 2 : digits;
    }
}
