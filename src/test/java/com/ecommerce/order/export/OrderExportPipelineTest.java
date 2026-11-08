package com.ecommerce.order.export;

import com.ecommerce.order.domain.Money;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderExportPipelineTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-10T12:00:00Z");

    private final Pipeline<Order, String> pipeline = Pipeline.<Order>of()
            .addFilter(new OrderSanitizationFilter())
            .addFilter(new CurrencyNormalizationFilter())
            .addFilter(new AuditTaxCalculationFilter())
            .addFilter(new CsvRowFormattingFilter());

    private static Order order(String id, String customerId, double price, String currency) {
        List<OrderItem> items = List.of(new OrderItem("P1", "Item", Money.of(price, currency), 1));
        return new Order(id, customerId, items, OrderStatus.PAID, CREATED_AT);
    }

    @Test
    @DisplayName("Pipeline stages run in the order they were added")
    void shouldComposeFilters() {
        Pipeline<String, Integer> lengthThenDouble = Pipeline.<String>of()
                .addFilter(String::length)
                .addFilter(length -> length * 2);

        assertThat(lengthThenDouble.execute("abc")).isEqualTo(6);
    }

    @Test
    @DisplayName("Full pipeline produces the audit row from the lab handout")
    void shouldProduceHandoutRow() {
        String row = pipeline.execute(order("ORD-EXP-1", "CUST-AUDIT", 200.00, "EUR"));

        assertThat(row).isEqualTo("\"ORD-EXP-1\",\"CUST-AUDIT\",\"PAID\",200.00,\"EUR\",216.00,21.60,\"2026-10-10T12:00:00Z\"");
    }

    @Test
    @DisplayName("USD orders pass through currency normalization unchanged")
    void shouldKeepUsdAmount() {
        String row = pipeline.execute(order("ORD-1", "CUST-1", 100.00, "USD"));

        assertThat(row).contains(",100.00,\"USD\",100.00,10.00,");
    }

    @Test
    @DisplayName("Sanitization neutralizes spreadsheet formulas and strips control characters")
    void shouldNeutralizeHostileText() {
        ExportStages.Sanitized sanitized = new OrderSanitizationFilter()
                .process(order("ORD-1", "=HYPERLINK(\"http://evil\")\r\nx", 1, "USD"));

        assertThat(sanitized.customerId()).startsWith("'=HYPERLINK").doesNotContain("\r", "\n");
    }

    @Test
    @DisplayName("CSV formatting doubles embedded quotes so a value cannot break out of its column")
    void shouldEscapeQuotes() {
        String row = pipeline.execute(order("ORD-1", "A\",\"B", 1, "USD"));

        assertThat(row).startsWith("\"ORD-1\",\"A\"\",\"\"B\",\"PAID\"");
    }

    @Test
    @DisplayName("Sanitization rejects a blank customer id")
    void shouldRejectBlankCustomer() {
        Order order = order("ORD-1", "   ", 1, "USD");

        assertThatThrownBy(() -> pipeline.execute(order)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Normalization rejects a currency without a rate instead of guessing")
    void shouldRejectUnknownCurrency() {
        Order order = order("ORD-1", "CUST-1", 1, "XXX");

        assertThatThrownBy(() -> pipeline.execute(order))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("XXX");
    }

    @Test
    @DisplayName("Exporter streams 10,000 orders: never more than one order in flight")
    void shouldStreamTenThousandOrders() throws IOException {
        int total = 10_000;
        AtomicLong generated = new AtomicLong();
        Stream<Order> orders = LongStream.range(0, total).mapToObj(i -> {
            generated.incrementAndGet();
            return order("ORD-" + i, "CUST-" + (i % 100), 10 + i % 90, i % 2 == 0 ? "USD" : "EUR");
        });
        InFlightCheckingWriter out = new InFlightCheckingWriter(generated);

        long rows = new OrderCsvExporter().export(orders, out);

        assertThat(rows).isEqualTo(total);
        assertThat(out.newlines).isEqualTo(total + 1L);
        assertThat(generated).hasValue(total);
        assertThat(out.maxInFlight).isEqualTo(1);
    }

    @Test
    @DisplayName("Exporter writes the header and rows, and saves a sample for the evidence")
    void shouldWriteSampleCsv() throws IOException {
        StringWriter out = new StringWriter();

        new OrderCsvExporter().export(Stream.of(
                order("ORD-EXP-1", "CUST-AUDIT", 200.00, "EUR"),
                order("ORD-EXP-2", "CUST-AUDIT", 59.97, "USD"),
                order("ORD-EXP-3", "=SUM(A1:A9)", 12.50, "EUR")), out);

        String[] lines = out.toString().split("\n");
        assertThat(lines).hasSize(4);
        assertThat(lines[0]).isEqualTo(CsvRowFormattingFilter.HEADER);

        Path sample = Path.of("target", "export", "order-export-sample.csv");
        Files.createDirectories(sample.getParent());
        Files.writeString(sample, out.toString());
    }

    /** Fails the "streaming" claim if the exporter pulls orders ahead of the rows it has written. */
    private static final class InFlightCheckingWriter extends Writer {

        private final AtomicLong generated;
        private long newlines;
        private long maxInFlight;

        private InFlightCheckingWriter(AtomicLong generated) {
            this.generated = generated;
        }

        @Override
        public void write(char[] buffer, int offset, int length) {
            long rowsWritten = Math.max(newlines - 1, 0);
            maxInFlight = Math.max(maxInFlight, generated.get() - rowsWritten);
            for (int i = offset; i < offset + length; i++) {
                if (buffer[i] == '\n') {
                    newlines++;
                }
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
