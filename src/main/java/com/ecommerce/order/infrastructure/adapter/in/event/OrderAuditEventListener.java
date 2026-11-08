package com.ecommerce.order.infrastructure.adapter.in.event;

import com.ecommerce.order.domain.event.OrderPlacedDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Event-driven adapter: records every placed order in an audit trail.
 * The Order aggregate does not know this class exists. Adding or removing audit
 * logging never touches the domain or the place-order use case.
 */
@Component
public class OrderAuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderAuditEventListener.class);

    private final List<OrderPlacedDomainEvent> auditLog = Collections.synchronizedList(new ArrayList<>());

    @EventListener
    public void onOrderPlaced(OrderPlacedDomainEvent event) {
        log.info("[AUDIT] Captured OrderPlaced event: ID={}, Customer={}, Total={}",
                event.orderId().value(), event.customerId().value(), event.orderTotal());
        auditLog.add(event);
    }

    public List<OrderPlacedDomainEvent> getRecordedAudits() {
        synchronized (auditLog) {
            return List.copyOf(auditLog);
        }
    }
}
