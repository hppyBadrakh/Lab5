package com.ecommerce.order.application;

public class InvalidOrderRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidOrderRequestException(String message) {
        super(message);
    }

    public InvalidOrderRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
