package com.ecommerce.order.application;

import com.ecommerce.order.domain.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(String id);

    List<Order> findByCustomerId(String customerId);

    List<Order> findAll();

    boolean existsById(String id);

    void deleteById(String id);
}
