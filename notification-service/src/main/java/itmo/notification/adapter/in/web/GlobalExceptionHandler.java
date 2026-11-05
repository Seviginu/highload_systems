package itmo.notification.adapter.in.web;

import itmo.notification.application.NotificationNotFound;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NotificationNotFound.class)
    public ResponseEntity<?> missing(NotificationNotFound error, ServerWebExchange exchange) {
        return response(HttpStatus.NOT_FOUND, error.getMessage(), exchange);
    }

    @ExceptionHandler({ConstraintViolationException.class, ServerWebInputException.class})
    public ResponseEntity<?> invalid(Exception error, ServerWebExchange exchange) {
        return response(HttpStatus.BAD_REQUEST, "Invalid notification request", exchange);
    }

    @ExceptionHandler(RejectedExecutionException.class)
    public ResponseEntity<?> busy(RejectedExecutionException error, ServerWebExchange exchange) {
        return response(
                HttpStatus.SERVICE_UNAVAILABLE, "Notifications are busy; retry later", exchange);
    }

    private ResponseEntity<?> response(
            HttpStatus status, String message, ServerWebExchange exchange) {
        return ResponseEntity.status(status)
                .body(
                        Map.of(
                                "timestamp",
                                Instant.now(),
                                "status",
                                status.value(),
                                "error",
                                status.getReasonPhrase(),
                                "message",
                                message,
                                "path",
                                exchange.getRequest().getPath().value(),
                                "fieldErrors",
                                List.of()));
    }
}
