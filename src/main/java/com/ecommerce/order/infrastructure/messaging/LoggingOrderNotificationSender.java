package com.ecommerce.order.infrastructure.messaging;

import com.ecommerce.order.domain.event.OrderPlacedDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Placeholder sender until Milestone 3 adds a real broker: it only logs the confirmation. */
@Component
public class LoggingOrderNotificationSender implements OrderNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingOrderNotificationSender.class);

    @Override
    public void sendOrderConfirmation(OrderPlacedDomainEvent event) {
        log.info("[Side-Channel OK] Order confirmation queued for customer={} order={} total={}",
                event.customerId().value(), event.orderId().value(), event.orderTotal());
    }
}
