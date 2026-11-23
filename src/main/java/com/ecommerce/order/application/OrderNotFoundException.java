package com.ecommerce.order.application;

public class OrderNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
    }
}
