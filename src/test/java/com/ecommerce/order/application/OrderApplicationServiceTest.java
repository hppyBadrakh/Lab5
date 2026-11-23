package com.ecommerce.order.application;

import com.ecommerce.order.domain.event.DomainEvent;
import com.ecommerce.order.domain.event.OrderCancelledDomainEvent;
import com.ecommerce.order.domain.event.OrderPlacedDomainEvent;
import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderId;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.model.PaymentResult;
import com.ecommerce.order.domain.model.PaymentResult.PaymentStatus;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase.AddressCommand;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase.OrderLineCommand;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase.PaymentReceipt;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase.PlaceOrderCommand;
import com.ecommerce.order.domain.port.out.DomainEventPublisher;
import com.ecommerce.order.domain.port.out.PaymentPort;
import com.ecommerce.order.infrastructure.adapter.out.persistence.InMemoryOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderApplicationServiceTest {

    private static final AddressCommand ADDRESS = new AddressCommand("Peace Ave 1", "Ulaanbaatar", "14200", "MN");
    private static final OrderLineCommand USD_LINE = new OrderLineCommand("ELEC-1001", new BigDecimal("25.50"), "USD", 2);
    private static final OrderLineCommand EUR_LINE = new OrderLineCommand("ELEC-2002", new BigDecimal("10.00"), "EUR", 1);

    private final InMemoryOrderRepository repository = new InMemoryOrderRepository();
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final ScriptedPaymentPort payments = new ScriptedPaymentPort();
    private final OrderApplicationService service = new OrderApplicationService(repository, publisher, payments);

    /** Answers every charge with the configured status and counts the calls. */
    private static final class ScriptedPaymentPort implements PaymentPort {

        private PaymentStatus nextStatus = PaymentStatus.SUCCESS;
        private int calls;
        private Money lastAmount;

        @Override
        public PaymentResult processPayment(OrderId orderId, Money amount) {
            calls++;
            lastAmount = amount;
            return new PaymentResult("REF-" + calls, orderId, nextStatus, nextStatus.name());
        }
    }

    /** Records what was published and whether the order was already stored at that moment. */
    private final class RecordingPublisher implements DomainEventPublisher {

        private final List<DomainEvent> published = new ArrayList<>();
        private boolean storedBeforePublish = true;

        @Override
        public void publish(List<DomainEvent> events) {
            for (DomainEvent event : events) {
                if (event instanceof OrderPlacedDomainEvent placed) {
                    storedBeforePublish &= repository.existsById(placed.orderId());
                }
                published.add(event);
            }
        }
    }

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
    @DisplayName("listOrdersForCustomer returns only that customer's orders and rejects a malformed id")
    void shouldListOrdersForCustomer() {
        Order mine = placed();
        service.placeOrder(command("CUST-2", List.of(USD_LINE)));

        assertThat(service.listOrdersForCustomer("CUST-1")).containsExactly(mine);
        assertThat(service.listOrdersForCustomer("CUST-NOBODY")).isEmpty();
        assertThatThrownBy(() -> service.listOrdersForCustomer("X".repeat(65)))
                .isInstanceOf(InvalidOrderRequestException.class);
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
                .hasMessageContaining("cannot be paid when it is CANCELLED");
        assertThat(payments.calls).as("a cancelled order is never sent to the gateway").isZero();
    }

    @Test
    @DisplayName("payOrder charges the order total and marks the order PAID on success")
    void shouldChargeTheOrderTotal() {
        String id = placed().getId().value();

        PaymentReceipt receipt = service.payOrder(id);

        assertThat(receipt.deferred()).isFalse();
        assertThat(receipt.order().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payments.lastAmount).isEqualTo(Money.of("51.00", "USD"));
    }

    @Test
    @DisplayName("A deferred payment leaves the order PAYMENT_PENDING; paying again settles it")
    void shouldDeferThenSettle() {
        String id = placed().getId().value();
        payments.nextStatus = PaymentStatus.DEFERRED_PENDING;

        PaymentReceipt deferred = service.payOrder(id);
        assertThat(deferred.deferred()).isTrue();
        assertThat(service.getOrder(id).getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);

        assertThat(service.payOrder(id).order().getStatus()).as("deferring twice is harmless")
                .isEqualTo(OrderStatus.PAYMENT_PENDING);

        payments.nextStatus = PaymentStatus.SUCCESS;
        assertThat(service.payOrder(id).order().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payments.calls).isEqualTo(3);
    }

    @Test
    @DisplayName("A declined payment is reported (402) and the order stays PLACED")
    void shouldReportDeclinedPayment() {
        String id = placed().getId().value();
        payments.nextStatus = PaymentStatus.DECLINED;

        assertThatThrownBy(() -> service.payOrder(id))
                .isInstanceOf(PaymentDeclinedException.class)
                .hasMessageContaining("declined");
        assertThat(service.getOrder(id).getStatus()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    @DisplayName("OrderPlaced is published once, after the order was saved")
    void shouldPublishOrderPlacedAfterSave() {
        Order order = placed();

        assertThat(publisher.published).singleElement()
                .isInstanceOfSatisfying(OrderPlacedDomainEvent.class,
                        event -> assertThat(event.orderId()).isEqualTo(order.getId()));
        assertThat(publisher.storedBeforePublish).isTrue();
    }

    @Test
    @DisplayName("Cancelling publishes OrderCancelled; rejected requests publish nothing")
    void shouldPublishOnlyForSuccessfulChanges() {
        assertThatThrownBy(() -> service.placeOrder(command("CUST-1", List.of(USD_LINE, EUR_LINE))))
                .isInstanceOf(InvalidOrderRequestException.class);
        assertThat(publisher.published).isEmpty();

        String id = placed().getId().value();
        service.payOrder(id);
        service.cancelOrder(id);
        assertThatThrownBy(() -> service.cancelOrder(id)).isInstanceOf(InvalidOrderOperationException.class);

        assertThat(publisher.published).hasSize(2);
        assertThat(publisher.published.get(1)).isInstanceOf(OrderCancelledDomainEvent.class);
    }
}
