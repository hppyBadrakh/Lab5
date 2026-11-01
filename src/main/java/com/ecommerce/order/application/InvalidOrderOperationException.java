package com.ecommerce.order.application;

public class InvalidOrderOperationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidOrderOperationException(String message) {
        super(message);
    }

    public InvalidOrderOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
