package com.ecommerce.order.presentation.rest;

import com.ecommerce.order.application.InvalidOrderOperationException;
import com.ecommerce.order.application.InvalidOrderRequestException;
import com.ecommerce.order.application.OrderNotFoundException;
import com.ecommerce.order.application.PaymentDeclinedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns every error into an RFC 7807 / RFC 9457 {@code application/problem+json} body.
 * The base class keeps Spring MVC errors (malformed JSON, 405, 415) at their
 * own status codes; without it the catch-all below would turn them into 500s.
 */
@RestControllerAdvice
public class GlobalRestExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleNotFound(OrderNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Order Not Found", ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderOperationException.class)
    public ProblemDetail handleConflict(InvalidOrderOperationException ex) {
        return problem(HttpStatus.CONFLICT, "State Transition Conflict", ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidOrderRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Business Rule Violation", ex.getMessage());
    }

    @ExceptionHandler(PaymentDeclinedException.class)
    public ProblemDetail handlePaymentDeclined(PaymentDeclinedException ex) {
        return problem(HttpStatus.PAYMENT_REQUIRED, "Payment Declined", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        logger.error("Unhandled exception", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Validation failed");
        Map<String, String> invalidFields = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> invalidFields.put(error.getField(), error.getDefaultMessage()));
        problem.setProperty("invalidFields", invalidFields);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
