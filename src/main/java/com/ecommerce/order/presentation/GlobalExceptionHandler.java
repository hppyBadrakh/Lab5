package com.ecommerce.order.presentation;

import com.ecommerce.order.application.InvalidOrderOperationException;
import com.ecommerce.order.application.InvalidOrderRequestException;
import com.ecommerce.order.application.OrderNotFoundException;
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
 * The base class keeps Spring MVC errors (malformed JSON, 405, 415) at their
 * own status codes; without it the catch-all below would turn them into 500s.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleNotFound(OrderNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderOperationException.class)
    public ProblemDetail handleConflict(InvalidOrderOperationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidOrderRequestException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
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
}
