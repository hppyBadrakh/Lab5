package com.ecommerce.order.infrastructure.adapter.in.event;

import com.ecommerce.order.domain.event.OrderPlacedDomainEvent;
import com.ecommerce.order.domain.model.CustomerId;
import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.OrderId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderAuditEventListenerTest {

    @Test
    @DisplayName("Records each OrderPlaced event and exposes a read-only copy")
    void shouldRecordEvents() {
        OrderAuditEventListener listener = new OrderAuditEventListener();
        OrderPlacedDomainEvent event = new OrderPlacedDomainEvent(UUID.randomUUID(), new OrderId("ORD-1"),
                new CustomerId("CUST-1"), Money.of("300.00", "USD"), Instant.now());

        listener.onOrderPlaced(event);

        assertThat(listener.getRecordedAudits()).containsExactly(event);
        assertThatThrownBy(() -> listener.getRecordedAudits().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
