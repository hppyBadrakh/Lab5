package com.ecommerce.order.infrastructure.adapter.out.event;

import com.ecommerce.order.domain.event.DomainEvent;
import com.ecommerce.order.domain.event.OrderCancelledDomainEvent;
import com.ecommerce.order.domain.event.OrderPlacedDomainEvent;
import com.ecommerce.order.domain.model.CustomerId;
import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.OrderId;
import com.ecommerce.order.domain.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SpringDomainEventPublisherTest {

    private static final DomainEvent PLACED = new OrderPlacedDomainEvent(UUID.randomUUID(), new OrderId("ORD-1"),
            new CustomerId("CUST-1"), Money.of("10.00", "USD"), Instant.now());
    private static final DomainEvent CANCELLED = new OrderCancelledDomainEvent(UUID.randomUUID(), new OrderId("ORD-1"),
            OrderStatus.PLACED, Instant.now());

    @Test
    @DisplayName("Forwards every event to the Spring event bus, in order")
    void shouldForwardEvents() {
        List<Object> received = new ArrayList<>();
        SpringDomainEventPublisher publisher = new SpringDomainEventPublisher(received::add);

        publisher.publish(List.of(PLACED, CANCELLED));

        assertThat(received).containsExactly(PLACED, CANCELLED);
    }

    @Test
    @DisplayName("A failing listener is isolated: no exception escapes and later events are still delivered")
    void shouldIsolateListenerFailures() {
        List<Object> received = new ArrayList<>();
        SpringDomainEventPublisher publisher = new SpringDomainEventPublisher(event -> {
            if (event == PLACED) {
                throw new IllegalStateException("audit store is down");
            }
            received.add(event);
        });

        assertThatCode(() -> publisher.publish(List.of(PLACED, CANCELLED))).doesNotThrowAnyException();
        assertThat(received).containsExactly(CANCELLED);
    }
}
