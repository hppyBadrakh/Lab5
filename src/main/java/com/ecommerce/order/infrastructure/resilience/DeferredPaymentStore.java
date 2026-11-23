package com.ecommerce.order.infrastructure.resilience;

import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.OrderId;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps payment intents that could not be settled because the gateway was unavailable.
 * In memory, like the order repository: a reconciliation worker that settles them later and a durable
 * store are follow-up work named in ADR-0008.
 */
@Component
public class DeferredPaymentStore {

    public record DeferredPaymentIntent(UUID token, OrderId orderId, Money amount, String reason, Instant deferredAt) {
    }

    private final Map<UUID, DeferredPaymentIntent> intents = new ConcurrentHashMap<>();
    private final Clock clock;

    public DeferredPaymentStore() {
        this(Clock.systemUTC());
    }

    DeferredPaymentStore(Clock clock) {
        this.clock = clock;
    }

    public UUID recordDeferredPaymentIntent(OrderId orderId, Money amount, String reason) {
        UUID token = UUID.randomUUID();
        intents.put(token, new DeferredPaymentIntent(token, orderId, amount, reason, clock.instant()));
        return token;
    }

    public List<DeferredPaymentIntent> pendingIntents() {
        return List.copyOf(intents.values());
    }

    public int count() {
        return intents.size();
    }
}
