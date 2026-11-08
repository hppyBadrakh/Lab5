package com.ecommerce.order.application;

import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderId;

import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(OrderId id);

    boolean existsById(OrderId id);
}
