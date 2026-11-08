package com.ecommerce.order.infrastructure.adapter.out.event;

import com.ecommerce.order.domain.event.DomainEvent;
import com.ecommerce.order.domain.port.out.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Driven adapter: forwards domain events to Spring's in-process event bus.
 * The order is already saved when this runs, so a failing listener is logged and isolated:
 * it must not fail the request or stop the remaining events and listeners.
 */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringDomainEventPublisher.class);

    private final ApplicationEventPublisher springPublisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher springPublisher) {
        this.springPublisher = springPublisher;
    }

    @Override
    public void publish(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            try {
                springPublisher.publishEvent(event);
            } catch (RuntimeException ex) {
                log.error("Listener failed for {} {}; the order change itself is kept",
                        event.getClass().getSimpleName(), event.eventId(), ex);
            }
        }
    }
}
