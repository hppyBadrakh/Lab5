package com.ecommerce.order.infrastructure.resilience;

import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.OrderId;
import com.ecommerce.order.domain.model.PaymentResult;
import com.ecommerce.order.domain.model.PaymentResult.PaymentStatus;
import com.ecommerce.order.domain.port.out.PaymentPort;
import com.ecommerce.order.infrastructure.resilience.gateway.ExternalPaymentClient;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Production adapter for the {@link PaymentPort} (ADR-0008, scenario S2).
 * <p>
 * A Resilience4j circuit breaker ({@value #CIRCUIT_BREAKER}) watches the last 10 gateway calls. When half of
 * them fail or are slow, it opens: for the next 5 seconds no request thread waits for the gateway any more.
 * The degraded fallback stores the payment intent and answers {@code DEFERRED_PENDING} straight away,
 * so the customer's checkout is kept instead of failing with a 5xx.
 * <p>
 * A semaphore bulkhead ({@value #BULKHEAD}) caps the calls that may wait for the gateway at the same time
 * (25). The breaker only learns that the gateway is slow when calls complete, so without the bulkhead the
 * first seconds of an outage could still occupy almost every Tomcat worker thread. A call that finds the
 * bulkhead full is deferred at once, through the same fallback.
 */
@Component
@ConditionalOnProperty(name = "payment.resilience.enabled", havingValue = "true", matchIfMissing = true)
public class ResilientPaymentAdapter implements PaymentPort {

    public static final String CIRCUIT_BREAKER = "paymentService";
    public static final String BULKHEAD = "paymentGateway";

    private static final Logger log = LoggerFactory.getLogger(ResilientPaymentAdapter.class);

    private final ExternalPaymentClient externalClient;
    private final DeferredPaymentStore fallbackStore;

    public ResilientPaymentAdapter(ExternalPaymentClient externalClient, DeferredPaymentStore fallbackStore) {
        this.externalClient = externalClient;
        this.fallbackStore = fallbackStore;
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "deferPayment")
    @Bulkhead(name = BULKHEAD)
    public PaymentResult processPayment(OrderId orderId, Money amount) {
        return externalClient.callRemoteGateway(orderId, amount);
    }

    /**
     * Degraded fallback. Runs when the gateway call failed, and immediately (without any network call)
     * while the circuit is OPEN ({@code CallNotPermittedException}) or the bulkhead is full
     * ({@code BulkheadFullException}).
     */
    PaymentResult deferPayment(OrderId orderId, Money amount, Throwable failure) {
        log.warn("[Payment Fallback] Gateway unavailable ({}: {}). Deferring settlement for order={}",
                failure.getClass().getSimpleName(), failure.getMessage(), orderId.value());
        UUID token = fallbackStore.recordDeferredPaymentIntent(orderId, amount, failure.getClass().getSimpleName());
        return new PaymentResult("DEFERRED-" + token, orderId, PaymentStatus.DEFERRED_PENDING,
                "Payment gateway temporarily unavailable. Payment queued for background settlement.");
    }
}
