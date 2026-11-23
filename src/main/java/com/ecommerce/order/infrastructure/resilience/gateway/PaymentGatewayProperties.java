package com.ecommerce.order.infrastructure.resilience.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Behaviour of the simulated payment gateway, so that load tests can reproduce a degraded gateway.
 *
 * @param latency        normal response time of the gateway
 * @param slowCallRatio  share of calls (0.0 to 1.0) that take {@code slowLatency} instead
 * @param slowLatency    response time of a slow call (scenario S2: more than 5 seconds)
 * @param readTimeout    how long our client waits before it gives up; a call slower than this fails
 */
@ConfigurationProperties(prefix = "payment.gateway")
public record PaymentGatewayProperties(Duration latency, double slowCallRatio, Duration slowLatency,
                                       Duration readTimeout) {

    public PaymentGatewayProperties {
        latency = latency == null ? Duration.ofMillis(50) : latency;
        slowLatency = slowLatency == null ? Duration.ofSeconds(5) : slowLatency;
        readTimeout = readTimeout == null ? Duration.ofSeconds(3) : readTimeout;
        if (slowCallRatio < 0.0 || slowCallRatio > 1.0) {
            throw new IllegalArgumentException("payment.gateway.slow-call-ratio must be between 0.0 and 1.0");
        }
    }
}
