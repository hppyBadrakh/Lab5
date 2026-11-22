package com.ecommerce.order.infrastructure.adapter.out.persistence;

import com.ecommerce.order.domain.model.CustomerId;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderId;
import com.ecommerce.order.domain.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.ecommerce.order.OrderFixtures.order;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryOrderRepositoryTest {

    private final InMemoryOrderRepository repository = new InMemoryOrderRepository();

    @Test
    @DisplayName("save then findById returns the same aggregate")
    void shouldSaveAndFind() {
        Order order = repository.save(order("ORD-1", OrderStatus.PLACED));

        assertThat(repository.findById(new OrderId("ORD-1"))).containsSame(order);
        assertThat(repository.existsById(new OrderId("ORD-1"))).isTrue();
    }

    @Test
    @DisplayName("findById is empty for an unknown id")
    void shouldReturnEmptyForUnknownId() {
        assertThat(repository.findById(new OrderId("UNKNOWN"))).isEmpty();
        assertThat(repository.existsById(new OrderId("UNKNOWN"))).isFalse();
    }

    @Test
    @DisplayName("findByCustomerId returns only that customer's orders, newest first")
    void shouldFindByCustomerNewestFirst() {
        Order older = repository.save(order("ORD-1", "CUST-1", "10.00", "USD", 1, OrderStatus.PLACED,
                Instant.parse("2026-11-01T00:00:00Z")));
        Order newer = repository.save(order("ORD-2", "CUST-1", "10.00", "USD", 1, OrderStatus.PAID,
                Instant.parse("2026-11-03T00:00:00Z")));
        repository.save(order("ORD-3", "CUST-2", "10.00", "USD", 1, OrderStatus.PLACED,
                Instant.parse("2026-11-02T00:00:00Z")));

        assertThat(repository.findByCustomerId(new CustomerId("CUST-1"))).containsExactly(newer, older);
        assertThat(repository.findByCustomerId(new CustomerId("CUST-NOBODY"))).isEmpty();
    }

    @Test
    @DisplayName("Saving the same id again replaces the stored aggregate")
    void shouldReplaceOnSave() {
        repository.save(order("ORD-1", OrderStatus.PLACED));
        Order cancelled = repository.save(order("ORD-1", OrderStatus.CANCELLED));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById(new OrderId("ORD-1"))).containsSame(cancelled);
    }
}
