package mn.edu.num.sad.ecommerce.product;

import java.math.BigDecimal;
import java.util.Objects;

public record Product(
        String id,
        String name,
        BigDecimal price
) {
    public Product {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(price, "price must not be null");
    }
}