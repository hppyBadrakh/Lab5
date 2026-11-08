package com.ecommerce.order.application;

import com.ecommerce.order.domain.model.CustomerId;
import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderId;
import com.ecommerce.order.domain.model.Quantity;
import com.ecommerce.order.domain.model.Sku;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase;
import com.ecommerce.order.domain.port.out.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.Currency;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Implements the inbound port. Use case orchestration only: load, call the aggregate, save.
 * Business rules live in {@link Order}. This class translates domain rejections into
 * application exceptions (400 for bad input, 409 for an illegal state change).
 */
@Service
public class OrderApplicationService implements ManageOrdersUseCase {

    private final OrderRepository orderRepository;

    public OrderApplicationService(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
    }

    @Override
    public Order placeOrder(PlaceOrderCommand command) {
        Objects.requireNonNull(command, "Command must not be null");
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new InvalidOrderRequestException("At least one line item required");
        }
        if (command.shippingAddress() == null) {
            throw new InvalidOrderRequestException("Shipping address required");
        }
        Order order;
        try {
            order = new Order(OrderId.generate(), new CustomerId(command.customerId()),
                    Currency.getInstance(command.lines().getFirst().currency()), command.shippingAddress().toDomain());
            for (OrderLineCommand line : command.lines()) {
                order.addLineItem(new Sku(line.sku()),
                        Money.of(line.unitPrice(), Currency.getInstance(line.currency())),
                        new Quantity(line.quantity()));
            }
            order.placeOrder();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InvalidOrderRequestException(ex.getMessage(), ex);
        }
        return orderRepository.save(order);
    }

    @Override
    public Order getOrder(String orderId) {
        return load(orderId);
    }

    @Override
    public Order payOrder(String orderId) {
        return change(orderId, Order::markAsPaid);
    }

    @Override
    public Order cancelOrder(String orderId) {
        return change(orderId, Order::cancel);
    }

    private Order change(String orderId, Consumer<Order> command) {
        Order order = load(orderId);
        try {
            command.accept(order);
        } catch (IllegalStateException ex) {
            throw new InvalidOrderOperationException(ex.getMessage(), ex);
        }
        return orderRepository.save(order);
    }

    private Order load(String orderId) {
        OrderId id;
        try {
            id = new OrderId(orderId);
        } catch (IllegalArgumentException ex) {
            throw new OrderNotFoundException(orderId);
        }
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
