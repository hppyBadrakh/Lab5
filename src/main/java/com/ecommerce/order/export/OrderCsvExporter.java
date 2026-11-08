package com.ecommerce.order.export;

import com.ecommerce.order.domain.Order;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.stream.Stream;

/** Writes one row at a time, so memory use does not grow with the number of orders. */
public class OrderCsvExporter {

    private final Pipeline<Order, String> pipeline = Pipeline.<Order>of()
            .addFilter(new OrderSanitizationFilter())
            .addFilter(new CurrencyNormalizationFilter())
            .addFilter(new AuditTaxCalculationFilter())
            .addFilter(new CsvRowFormattingFilter());

    public long export(Stream<Order> orders, Writer out) throws IOException {
        out.write(CsvRowFormattingFilter.HEADER + "\n");
        long rows = 0;
        for (Iterator<Order> it = orders.iterator(); it.hasNext(); rows++) {
            out.write(pipeline.execute(it.next()) + "\n");
        }
        out.flush();
        return rows;
    }
}
