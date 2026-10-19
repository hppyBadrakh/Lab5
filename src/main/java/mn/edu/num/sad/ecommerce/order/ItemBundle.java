package mn.edu.num.sad.ecommerce.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ItemBundle implements OrderItemComponent {

    private final String name;
    private final List<OrderItemComponent> children = new ArrayList<>();

    public ItemBundle(String name) {
        Objects.requireNonNull(name, "name must not be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }

        this.name = name;
    }

    public ItemBundle add(OrderItemComponent component) {
        children.add(Objects.requireNonNull(
                component,
                "component must not be null"
        ));
        return this;
    }

    public List<OrderItemComponent> children() {
        return List.copyOf(children);
    }

    @Override
    public BigDecimal price() {
        return children.stream()
                .map(OrderItemComponent::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public String render() {
        StringBuilder result = new StringBuilder(name).append(" [");

        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(children.get(i).render());
        }

        return result.append("] = ").append(price().toPlainString())
                .toString();
    }
}