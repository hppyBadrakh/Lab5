package com.ecommerce.order.application;

/** The payment gateway answered and refused the charge. The order stays payable. Mapped to 402. */
public class PaymentDeclinedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PaymentDeclinedException(String message) {
        super(message);
    }
}
