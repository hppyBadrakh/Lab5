package com.ecommerce.order.export;

import com.ecommerce.order.domain.model.Money;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;

/** Converts amounts to USD. The rates are fixed sample values until a rate source exists. */
public class CurrencyNormalizationFilter implements Filter<ExportStages.Sanitized, ExportStages.Normalized> {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Map<String, BigDecimal> USD_RATES = Map.of(
            "USD", BigDecimal.ONE,
            "EUR", new BigDecimal("1.08"));

    @Override
    public ExportStages.Normalized process(ExportStages.Sanitized order) {
        String currency = order.amount().currencyCode();
        BigDecimal rate = USD_RATES.get(currency);
        if (rate == null) {
            throw new IllegalArgumentException("No USD rate for currency: " + currency);
        }
        return new ExportStages.Normalized(order, Money.of(order.amount().amount().multiply(rate), USD));
    }
}
