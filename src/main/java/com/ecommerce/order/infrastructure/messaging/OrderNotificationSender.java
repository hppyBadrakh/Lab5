package com.ecommerce.order.infrastructure.messaging;

import com.ecommerce.order.domain.event.OrderPlacedDomainEvent;

/**
 * The downstream end of the side-channel: tells the customer that the order was received.
 * Today a log line stands in for it. A broker or e-mail client replaces it by providing another bean,
 * without touching the listener, the use case or the domain.
 */
public interface OrderNotificationSender {

    void sendOrderConfirmation(OrderPlacedDomainEvent event);
}
