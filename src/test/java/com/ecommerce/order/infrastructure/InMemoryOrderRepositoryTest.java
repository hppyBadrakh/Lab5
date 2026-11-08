package com.ecommerce.order.infrastructure;

import com.ecommerce.order.domain.Money;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryOrderRepositoryTest {

    private final InMemoryOrderRepository repository = new InMemoryOrderRepository();

    private static Order order(String customerId) {
        return new Order(customerId, List.of(new OrderItem("P1", "Item", Money.of(1, "USD"), 1)));
    }

    @Test
    @DisplayName("save then findById returns the order")
    void shouldSaveAndFind() {
        Order order = repository.save(order("CUST-1"));

        assertThat(repository.findById(order.getId())).contains(order);
        assertThat(repository.existsById(order.getId())).isTrue();
    }

    @Test
    @DisplayName("findById is empty for an unknown id")
    void shouldReturnEmptyForUnknownId() {
        assertThat(repository.findById("UNKNOWN")).isEmpty();
    }

    @Test
    @DisplayName("findByCustomerId returns only that customer's orders")
    void shouldFilterByCustomer() {
        Order mine = repository.save(order("CUST-1"));
        repository.save(order("CUST-2"));

        assertThat(repository.findByCustomerId("cust-1")).containsExactly(mine);
        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("deleteById removes the order")
    void shouldDelete() {
        Order order = repository.save(order("CUST-1"));

        repository.deleteById(order.getId());

        assertThat(repository.existsById(order.getId())).isFalse();
    }
}
