package mn.edu.num.sad.ecommerce.order;

import java.math.BigDecimal;

public interface OrderItemComponent {

    BigDecimal price();

    String render();
}