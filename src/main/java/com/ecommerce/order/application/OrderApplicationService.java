package com.ecommerce.order.application;

import com.ecommerce.order.domain.model.CustomerId;
import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderId;
import com.ecommerce.order.domain.model.PaymentResult;
import com.ecommerce.order.domain.model.Quantity;
import com.ecommerce.order.domain.model.Sku;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase;
import com.ecommerce.order.domain.port.out.DomainEventPublisher;
import com.ecommerce.order.domain.port.out.OrderRepository;
import com.ecommerce.order.domain.port.out.PaymentPort;
import org.springframework.stereotype.Service;

import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Implements the inbound port. Use case orchestration only: load, call the aggregate, save, publish events.
 * Business rules live in {@link Order}. This class translates domain rejections into
 * application exceptions (400 for bad input, 409 for an illegal state change).
 */
@Service
public class OrderApplicationService implements ManageOrdersUseCase {

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final PaymentPort paymentPort;

    public OrderApplicationService(OrderRepository orderRepository, DomainEventPublisher eventPublisher,
                                   PaymentPort paymentPort) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.paymentPort = Objects.requireNonNull(paymentPort);
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
        return saveAndPublish(order);
    }

    @Override
    public Order getOrder(String orderId) {
        return load(orderId);
    }

    @Override
    public List<Order> listOrdersForCustomer(String customerId) {
        CustomerId id;
        try {
            id = new CustomerId(customerId);
        } catch (IllegalArgumentException ex) {
            throw new InvalidOrderRequestException(ex.getMessage(), ex);
        }
        return orderRepository.findByCustomerId(id);
    }

    /**
     * The payability check runs before the gateway is called, so a cancelled or paid order is never charged.
     * How the gateway call is protected (circuit breaker, deferral) is decided by the PaymentPort adapter.
     */
    @Override
    public PaymentReceipt payOrder(String orderId) {
        Order order = load(orderId);
        try {
            order.assertPayable();
        } catch (IllegalStateException ex) {
            throw new InvalidOrderOperationException(ex.getMessage(), ex);
        }
        PaymentResult payment = paymentPort.processPayment(order.getId(), order.calculateTotal());
        try {
            switch (payment.status()) {
                case SUCCESS -> order.markAsPaid();
                case DEFERRED_PENDING -> order.markPaymentDeferred();
                case DECLINED -> throw new PaymentDeclinedException(
                        "Payment for order " + orderId + " was declined: " + payment.message());
            }
        } catch (IllegalStateException ex) {
            throw new InvalidOrderOperationException(ex.getMessage(), ex);
        }
        return new PaymentReceipt(saveAndPublish(order), payment);
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
        return saveAndPublish(order);
    }

    /** Events are published only after the save succeeded, so listeners never see an order that was not stored. */
    private Order saveAndPublish(Order order) {
        Order saved = orderRepository.save(order);
        eventPublisher.publish(saved.pullDomainEvents());
        return saved;
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
