package com.ecommerce.order.application;

import com.ecommerce.order.application.OrderApplicationService.OrderItemCommand;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.infrastructure.InMemoryOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderApplicationServiceTest {

    private static final OrderItemCommand USD_ITEM = new OrderItemCommand("P1", "Keyboard", 25.50, "USD", 2);
    private static final OrderItemCommand EUR_ITEM = new OrderItemCommand("P2", "Mouse", 10.00, "EUR", 1);

    private final OrderRepository repository = new InMemoryOrderRepository();
    private final OrderApplicationService service = new OrderApplicationService(repository);

    @Test
    @DisplayName("createOrder maps commands to a persisted PENDING order")
    void shouldCreateOrder() {
        Order order = service.createOrder("CUST-1", List.of(USD_ITEM));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.calculateTotal().amount()).isEqualByComparingTo("51.00");
        assertThat(repository.existsById(order.getId())).isTrue();
    }

    @Test
    @DisplayName("createOrder rejects a blank customer id")
    void shouldRejectBlankCustomer() {
        assertThatThrownBy(() -> service.createOrder("  ", List.of(USD_ITEM)))
                .isInstanceOf(InvalidOrderRequestException.class);
    }

    @Test
    @DisplayName("createOrder rejects missing items")
    void shouldRejectMissingItems() {
        assertThatThrownBy(() -> service.createOrder("CUST-1", null))
                .isInstanceOf(InvalidOrderRequestException.class);
        assertThatThrownBy(() -> service.createOrder("CUST-1", List.of()))
                .isInstanceOf(InvalidOrderRequestException.class);
    }

    @Test
    @DisplayName("Domain rejection of mixed currencies is translated and nothing is persisted")
    void shouldTranslateDomainRejection() {
        assertThatThrownBy(() -> service.createOrder("CUST-1", List.of(USD_ITEM, EUR_ITEM)))
                .isInstanceOf(InvalidOrderRequestException.class)
                .hasMessageContaining("same currency");
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("A non-finite price is a request error, not a server error")
    void shouldRejectNonFinitePrice() {
        OrderItemCommand infinite = new OrderItemCommand("P1", "Keyboard", Double.POSITIVE_INFINITY, "USD", 1);

        assertThatThrownBy(() -> service.createOrder("CUST-1", List.of(infinite)))
                .isInstanceOf(InvalidOrderRequestException.class);
    }

    @Test
    @DisplayName("getOrderById throws for an unknown id")
    void shouldThrowWhenNotFound() {
        assertThatThrownBy(() -> service.getOrderById("UNKNOWN"))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    @DisplayName("cancelOrder cancels a pending order and persists it")
    void shouldCancel() {
        Order order = service.createOrder("CUST-1", List.of(USD_ITEM));

        service.cancelOrder(order.getId());

        assertThat(service.getOrderById(order.getId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Cancelling twice is an invalid operation")
    void shouldRejectSecondCancel() {
        Order order = service.createOrder("CUST-1", List.of(USD_ITEM));
        service.cancelOrder(order.getId());

        assertThatThrownBy(() -> service.cancelOrder(order.getId()))
                .isInstanceOf(InvalidOrderOperationException.class)
                .hasMessageContaining("CANCELLED");
    }
}
