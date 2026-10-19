package mn.edu.num.sad.ecommerce.checkout;

import java.math.BigDecimal;

public interface PaymentGateway {

    String charge(
            String customerId,
            BigDecimal amount,
            String paymentToken
    );
}