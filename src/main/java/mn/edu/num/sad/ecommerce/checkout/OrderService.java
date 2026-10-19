package mn.edu.num.sad.ecommerce.checkout;

import java.math.BigDecimal;

public interface OrderService {

    String createOrder(
            String customerId,
            String sku,
            int quantity,
            BigDecimal totalAmount
    );
}