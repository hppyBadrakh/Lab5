package com.ecommerce.order.application;

import com.ecommerce.order.domain.port.in.ManageOrdersUseCase.AddressCommand;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase.OrderLineCommand;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase.PlaceOrderCommand;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.infrastructure.adapter.out.persistence.InMemoryOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderApplicationServiceTest {

    private static final AddressCommand ADDRESS = new AddressCommand("Peace Ave 1", "Ulaanbaatar", "14200", "MN");
    private static final OrderLineCommand USD_LINE = new OrderLineCommand("ELEC-1001", new BigDecimal("25.50"), "USD", 2);
    private static final OrderLineCommand EUR_LINE = new OrderLineCommand("ELEC-2002", new BigDecimal("10.00"), "EUR", 1);

    private final InMemoryOrderRepository repository = new InMemoryOrderRepository();
    private final OrderApplicationService service = new OrderApplicationService(repository);

    private static PlaceOrderCommand command(String customerId, List<OrderLineCommand> lines) {
        return new PlaceOrderCommand(customerId, ADDRESS, lines);
    }

    private Order placed() {
        return service.placeOrder(command("CUST-1", List.of(USD_LINE)));
    }

    @Test
    @DisplayName("placeOrder builds the aggregate, places it and persists it")
    void shouldPlaceOrder() {
        Order order = placed();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.calculateTotal().amount()).isEqualByComparingTo("51.00");
        assertThat(order.getShippingAddress().city()).isEqualTo("Ulaanbaatar");
        assertThat(repository.existsById(order.getId())).isTrue();
    }

    @Test
    @DisplayName("placeOrder rejects a blank customer id, missing lines and a missing address")
    void shouldRejectInvalidRequests() {
        assertThatThrownBy(() -> service.placeOrder(command("  ", List.of(USD_LINE))))
                .isInstanceOf(InvalidOrderRequestException.class);
        assertThatThrownBy(() -> service.placeOrder(command("CUST-1", null)))
                .isInstanceOf(InvalidOrderRequestException.class);
        assertThatThrownBy(() -> service.placeOrder(command("CUST-1", List.of())))
                .isInstanceOf(InvalidOrderRequestException.class);
        assertThatThrownBy(() -> service.placeOrder(new PlaceOrderCommand("CUST-1", null, List.of(USD_LINE))))
                .isInstanceOf(InvalidOrderRequestException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("Domain rejections (currency, SKU format, limit) become request errors and nothing is persisted")
    void shouldTranslateDomainRejections() {
        assertThatThrownBy(() -> service.placeOrder(command("CUST-1", List.of(USD_LINE, EUR_LINE))))
                .isInstanceOf(InvalidOrderRequestException.class)
                .hasMessageContaining("Currency mismatch");
        assertThatThrownBy(() -> service.placeOrder(command("CUST-1",
                List.of(new OrderLineCommand("p1", BigDecimal.ONE, "USD", 1)))))
                .isInstanceOf(InvalidOrderRequestException.class)
                .hasMessageContaining("Invalid SKU");
        assertThatThrownBy(() -> service.placeOrder(command("CUST-1",
                List.of(new OrderLineCommand("ELEC-1001", new BigDecimal("5000.01"), "USD", 2)))))
                .isInstanceOf(InvalidOrderRequestException.class)
                .hasMessageContaining("limit");
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("getOrder throws for an unknown or malformed id")
    void shouldThrowWhenNotFound() {
        assertThatThrownBy(() -> service.getOrder("UNKNOWN"))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
        assertThatThrownBy(() -> service.getOrder(" ")).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("payOrder and cancelOrder change the stored aggregate")
    void shouldPayThenCancel() {
        Order order = placed();
        String id = order.getId().value();

        service.payOrder(id);
        assertThat(service.getOrder(id).getStatus()).isEqualTo(OrderStatus.PAID);

        service.cancelOrder(id);
        assertThat(service.getOrder(id).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Illegal transitions become invalid operations (409)")
    void shouldRejectIllegalTransitions() {
        String id = placed().getId().value();
        service.cancelOrder(id);

        assertThatThrownBy(() -> service.cancelOrder(id))
                .isInstanceOf(InvalidOrderOperationException.class)
                .hasMessageContaining("CANCELLED");
        assertThatThrownBy(() -> service.payOrder(id))
                .isInstanceOf(InvalidOrderOperationException.class)
                .hasMessageContaining("CANCELLED to PAID");
    }
}
