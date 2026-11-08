package com.ecommerce.order.export;

import java.util.List;

public class CsvRowFormattingFilter implements Filter<ExportStages.Taxed, String> {

    public static final String HEADER = "order_id,customer_id,status,amount,currency,amount_usd,tax_usd,created_at";

    @Override
    public String process(ExportStages.Taxed taxed) {
        ExportStages.Normalized normalized = taxed.order();
        ExportStages.Sanitized order = normalized.order();
        return String.join(",", List.of(
                quote(order.orderId()),
                quote(order.customerId()),
                quote(order.status()),
                order.amount().amount().toPlainString(),
                quote(order.amount().currency()),
                normalized.amountUsd().amount().toPlainString(),
                taxed.taxUsd().amount().toPlainString(),
                quote(order.createdAt().toString())));
    }

    private static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
