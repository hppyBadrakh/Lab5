package mn.edu.num.sad.ecommerce.catalogue;

import java.math.BigDecimal;
import java.util.Objects;

public record CatalogueItem(
        String id,
        String name,
        BigDecimal price
) {
    public CatalogueItem {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(price, "price must not be null");
    }
}