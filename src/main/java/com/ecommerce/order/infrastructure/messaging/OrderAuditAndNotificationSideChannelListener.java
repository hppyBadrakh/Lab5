package com.ecommerce.order.infrastructure.messaging;

import com.ecommerce.order.domain.event.OrderPlacedDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Asynchronous event side-channel: after an order was placed, records it in the audit trail and
 * sends the customer a confirmation.
 * <ul>
 *   <li><b>Non-blocking:</b> {@code @Async} hands the event to the side-channel thread pool, so the
 *       {@code 201 Created} response is sent without waiting for this method.</li>
 *   <li><b>Failure-isolated:</b> nothing thrown here can reach the request thread, and the order is
 *       already saved when the event is published.</li>
 *   <li><b>Decoupled:</b> the Order aggregate and the place-order use case do not know this class exists.</li>
 * </ul>
 */
@Component
public class OrderAuditAndNotificationSideChannelListener {

    private static final Logger log = LoggerFactory.getLogger(OrderAuditAndNotificationSideChannelListener.class);

    private final OrderNotificationSender notificationSender;
    private final List<OrderPlacedDomainEvent> auditLog = Collections.synchronizedList(new ArrayList<>());

    public OrderAuditAndNotificationSideChannelListener(OrderNotificationSender notificationSender) {
        this.notificationSender = notificationSender;
    }

    @Async(SideChannelConfiguration.EXECUTOR)
    @EventListener
    public void onOrderPlaced(OrderPlacedDomainEvent event) {
        try {
            log.info("[Side-Channel IN] Auditing OrderPlaced event: order={}, customer={}, total={}",
                    event.orderId().value(), event.customerId().value(), event.orderTotal());
            auditLog.add(event);
            notificationSender.sendOrderConfirmation(event);
        } catch (RuntimeException ex) {
            log.error("[Side-Channel ERROR] Side-channel failed for order={}; the order itself is kept",
                    event.orderId().value(), ex);
        }
    }

    public List<OrderPlacedDomainEvent> getRecordedAudits() {
        synchronized (auditLog) {
            return List.copyOf(auditLog);
        }
    }
}
