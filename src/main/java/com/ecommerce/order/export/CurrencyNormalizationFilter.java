package com.ecommerce.order.export;

import com.ecommerce.order.domain.Money;

import java.util.Locale;
import java.util.Map;

/** Converts amounts to USD. The rates are fixed sample values until a rate source exists. */
public class CurrencyNormalizationFilter implements Filter<ExportStages.Sanitized, ExportStages.Normalized> {

    private static final Map<String, Double> USD_RATES = Map.of("USD", 1.0, "EUR", 1.08);

    @Override
    public ExportStages.Normalized process(ExportStages.Sanitized order) {
        String currency = order.amount().currency().toUpperCase(Locale.ROOT);
        Double rate = USD_RATES.get(currency);
        if (rate == null) {
            throw new IllegalArgumentException("No USD rate for currency: " + currency);
        }
        return new ExportStages.Normalized(order, new Money(order.amount().multiply(rate).amount(), "USD"));
    }
}
