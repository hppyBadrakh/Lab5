package com.ecommerce.order.infrastructure.adapter.out.persistence;

import com.ecommerce.order.domain.port.out.OrderRepository;
import com.ecommerce.order.domain.model.CustomerId;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderId;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<OrderId, Order> store = new ConcurrentHashMap<>();

    @Override
    public Order save(Order order) {
        store.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public boolean existsById(OrderId id) {
        return store.containsKey(id);
    }

    @Override
    public List<Order> findByCustomerId(CustomerId customerId) {
        return store.values().stream()
                .filter(order -> order.getCustomerId().equals(customerId))
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed()
                        .thenComparing(order -> order.getId().value()))
                .toList();
    }

    /** Number of stored orders. Adapter-specific, used by tests. Not part of the port. */
    public int count() {
        return store.size();
    }
}
