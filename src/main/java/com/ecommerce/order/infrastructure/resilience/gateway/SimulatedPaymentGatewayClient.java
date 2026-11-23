package com.ecommerce.order.infrastructure.resilience.gateway;

import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.OrderId;
import com.ecommerce.order.domain.model.PaymentResult;
import com.ecommerce.order.domain.model.PaymentResult.PaymentStatus;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stand-in for the real gateway (there is no sandbox account in this course). It blocks the calling
 * thread exactly like a synchronous HTTP client would: for the gateway latency, or until the read
 * timeout expires, after which it throws {@link PaymentGatewayException}.
 */
@Component
@EnableConfigurationProperties(PaymentGatewayProperties.class)
public class SimulatedPaymentGatewayClient implements ExternalPaymentClient {

    private final PaymentGatewayProperties properties;

    public SimulatedPaymentGatewayClient(PaymentGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentResult callRemoteGateway(OrderId orderId, Money amount) {
        boolean slow = ThreadLocalRandom.current().nextDouble() < properties.slowCallRatio();
        Duration latency = slow ? properties.slowLatency() : properties.latency();
        if (latency.compareTo(properties.readTimeout()) > 0) {
            block(properties.readTimeout());
            throw new PaymentGatewayException("Payment gateway did not answer within " + properties.readTimeout().toMillis()
                    + " ms for order " + orderId.value());
        }
        block(latency);
        return new PaymentResult("TX-" + UUID.randomUUID(), orderId, PaymentStatus.SUCCESS,
                "Charged " + amount);
    }

    private static void block(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("Interrupted while waiting for the payment gateway");
        }
    }
}
