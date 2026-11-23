package com.ecommerce.order.domain.port.out;

import com.ecommerce.order.domain.event.DomainEvent;

import java.util.List;

/**
 * Outbound port: hands domain events to whoever listens, after the aggregate was saved.
 * Implementations must not let a failing listener undo or fail the use case that raised the event.
 */
public interface DomainEventPublisher {

    void publish(List<DomainEvent> events);
}
