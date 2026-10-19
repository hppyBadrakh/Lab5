package mn.edu.num.sad.ecommerce.order;

import java.math.BigDecimal;
import java.util.Objects;

public record SingleItem(
        String name,
        int quantity,
        BigDecimal unitPrice
) implements OrderItemComponent {

    public SingleItem {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
    }

    @Override
    public BigDecimal price() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    @Override
    public String render() {
        return name + " x" + quantity + " = " + price().toPlainString();
    }
}