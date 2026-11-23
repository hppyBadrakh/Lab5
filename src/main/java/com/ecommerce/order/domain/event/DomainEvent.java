package com.ecommerce.order.domain.event;

import java.time.Instant;
import java.util.UUID;

/** A fact that happened inside the Order context, named in the past tense. Immutable. */
public interface DomainEvent {

    UUID eventId();

    Instant occurredOn();
}
