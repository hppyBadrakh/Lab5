package com.ecommerce.order.application;

import com.ecommerce.order.domain.Money;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class OrderApplicationService {

    private final OrderRepository orderRepository;

    public OrderApplicationService(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
    }

    public Order createOrder(String customerId, List<OrderItemCommand> itemCommands) {
        if (customerId == null || customerId.isBlank()) {
            throw new InvalidOrderRequestException("Customer ID required");
        }
        if (itemCommands == null || itemCommands.isEmpty()) {
            throw new InvalidOrderRequestException("At least one item required");
        }
        try {
            List<OrderItem> domainItems = itemCommands.stream()
                    .map(cmd -> new OrderItem(cmd.productId(), cmd.productName(),
                            Money.of(cmd.unitPrice(), cmd.currency()), cmd.quantity()))
                    .toList();
            return orderRepository.save(new Order(customerId, domainItems));
        } catch (IllegalArgumentException ex) {
            throw new InvalidOrderRequestException(ex.getMessage(), ex);
        }
    }

    public Order getOrderById(String orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public Order cancelOrder(String orderId) {
        Order order = getOrderById(orderId);
        try {
            order.cancel();
        } catch (IllegalStateException ex) {
            throw new InvalidOrderOperationException(ex.getMessage(), ex);
        }
        return orderRepository.save(order);
    }

    public record OrderItemCommand(String productId, String productName, double unitPrice,
                                   String currency, int quantity) {
    }
}
