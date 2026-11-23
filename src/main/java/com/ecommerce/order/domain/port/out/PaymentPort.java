package com.ecommerce.order.domain.port.out;

import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.OrderId;
import com.ecommerce.order.domain.model.PaymentResult;

/**
 * Outbound (driven) port to the Payment context (a Generic subdomain behind an anti-corruption layer).
 * The domain states what it needs; how the remote gateway is protected against latency and outages
 * (circuit breaker, fallback) is an adapter decision in {@code infrastructure.resilience}.
 */
public interface PaymentPort {

    /** Charges the order total. A resilient adapter never blocks on an unavailable gateway; it defers instead. */
    PaymentResult processPayment(OrderId orderId, Money amount);
}
