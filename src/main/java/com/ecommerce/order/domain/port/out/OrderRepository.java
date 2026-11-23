package com.ecommerce.order.domain.port.out;

import com.ecommerce.order.domain.model.CustomerId;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderId;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(OrderId id);

    boolean existsById(OrderId id);

    /** Every order of one customer, newest first. Empty if the customer has none. */
    List<Order> findByCustomerId(CustomerId customerId);
}
