package com.ecommerce.order.infrastructure.resilience.gateway;

/** The payment gateway timed out or could not be reached. Recorded as a failure by the circuit breaker. */
public class PaymentGatewayException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PaymentGatewayException(String message) {
        super(message);
    }
}
