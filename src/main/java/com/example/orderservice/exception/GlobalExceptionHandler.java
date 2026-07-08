package com.example.orderservice.exception;

import com.example.orderservice.dto.ErrorResponse;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(OrderNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(IllegalStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleIllegalTransition(IllegalStatusTransitionException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "ILLEGAL_STATUS_TRANSITION", ex.getMessage(), req);
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrder(InvalidOrderException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_ORDER", ex.getMessage(), req);
    }

    @ExceptionHandler(UnknownSortRuleException.class)
    public ResponseEntity<ErrorResponse> handleUnknownSort(UnknownSortRuleException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "UNKNOWN_SORT_RULE", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());
        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR",
                "Request payload failed validation", req.getRequestURI(), details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        // Covers malformed JSON and, via fail-on-unknown-properties, attempts to
        // set server-managed fields (orderId, status, totalAmount, timestamps).
        Throwable cause = ex.getMostSpecificCause();
        String message = cause instanceof UnrecognizedPropertyException upe
                ? "Unrecognized field '" + upe.getPropertyName() + "': this field is server-managed and cannot be set by the client"
                : "Malformed request body";
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", message, req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        // Deliberately generic: no stack trace or exception class name leaks to
        // the client (information-disclosure hardening).
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", req);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message, HttpServletRequest req) {
        ErrorResponse body = new ErrorResponse(status.value(), error, message, req.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
