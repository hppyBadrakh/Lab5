package mn.edu.num.sad.ecommerce.checkout;

import java.math.BigDecimal;
import java.time.Instant;

public record Receipt(
        String orderId,
        String customerId,
        BigDecimal totalAmount,
        String transactionReference,
        Instant createdAt
) {
}