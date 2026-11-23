package com.ecommerce.order.infrastructure.messaging;

import com.ecommerce.order.domain.event.OrderPlacedDomainEvent;
import com.ecommerce.order.domain.model.CustomerId;
import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.OrderId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderAuditAndNotificationSideChannelListenerTest {

    private static final OrderPlacedDomainEvent EVENT = new OrderPlacedDomainEvent(UUID.randomUUID(),
            new OrderId("ORD-1"), new CustomerId("CUST-1"), Money.of("300.00", "USD"), Instant.now());

    @Test
    @DisplayName("Audits each OrderPlaced event, sends the confirmation and exposes a read-only audit copy")
    void shouldAuditAndNotify() {
        List<OrderPlacedDomainEvent> sent = new ArrayList<>();
        OrderAuditAndNotificationSideChannelListener listener =
                new OrderAuditAndNotificationSideChannelListener(sent::add);

        listener.onOrderPlaced(EVENT);

        assertThat(listener.getRecordedAudits()).containsExactly(EVENT);
        assertThat(sent).containsExactly(EVENT);
        assertThatThrownBy(() -> listener.getRecordedAudits().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("A failing sender is contained: no exception escapes and the audit entry is kept")
    void shouldContainSenderFailure() {
        OrderAuditAndNotificationSideChannelListener listener =
                new OrderAuditAndNotificationSideChannelListener(event -> {
                    throw new IllegalStateException("mail server is down");
                });

        assertThatCode(() -> listener.onOrderPlaced(EVENT)).doesNotThrowAnyException();
        assertThat(listener.getRecordedAudits()).containsExactly(EVENT);
    }
}
