package com.ecommerce.order.infrastructure.resilience;

import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.OrderId;
import com.ecommerce.order.domain.model.PaymentResult;
import com.ecommerce.order.domain.port.out.PaymentPort;
import com.ecommerce.order.infrastructure.resilience.gateway.ExternalPaymentClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Unmitigated baseline, used only to measure the problem (docs/quality/measurement-evidence.md).
 * Active only with {@code payment.resilience.enabled=false}. Every request thread waits for the gateway
 * until it answers or the read timeout expires, and a gateway failure surfaces as an HTTP 500.
 */
@Component
@ConditionalOnProperty(name = "payment.resilience.enabled", havingValue = "false")
public class UnprotectedPaymentAdapter implements PaymentPort {

    private final ExternalPaymentClient externalClient;

    public UnprotectedPaymentAdapter(ExternalPaymentClient externalClient) {
        this.externalClient = externalClient;
    }

    @Override
    public PaymentResult processPayment(OrderId orderId, Money amount) {
        return externalClient.callRemoteGateway(orderId, amount);
    }
}
