package itmo.files.adapter.in.web;

import itmo.files.application.*;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(FileNotFound.class)
    public ResponseEntity<?> missing(FileNotFound error, ServerWebExchange exchange) {
        return response(HttpStatus.NOT_FOUND, error.getMessage(), exchange);
    }

    @ExceptionHandler({
        ConstraintViolationException.class,
        ServerWebInputException.class,
        org.springframework.core.codec.DecodingException.class
    })
    public ResponseEntity<?> invalid(Exception error, ServerWebExchange exchange) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.springframework.core.io.buffer.DataBufferLimitException)
                return response(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "File exceeds the configured size limit",
                        exchange);
        }
        return response(HttpStatus.BAD_REQUEST, "Invalid file request", exchange);
    }

    @ExceptionHandler(FileForbidden.class)
    public ResponseEntity<?> forbidden(FileForbidden error, ServerWebExchange exchange) {
        return response(HttpStatus.FORBIDDEN, error.getMessage(), exchange);
    }

    @ExceptionHandler({
        FileTooLarge.class,
        org.springframework.core.io.buffer.DataBufferLimitException.class
    })
    public ResponseEntity<?> large(Exception error, ServerWebExchange exchange) {
        return response(
                HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the configured size limit", exchange);
    }

    @ExceptionHandler({InvalidFile.class, IllegalArgumentException.class})
    public ResponseEntity<?> invalidFile(Exception error, ServerWebExchange exchange) {
        return response(HttpStatus.BAD_REQUEST, "Invalid file request", exchange);
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<?> storage(Exception error, ServerWebExchange exchange) {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store or retrieve file", exchange);
    }

    @ExceptionHandler(RejectedExecutionException.class)
    public ResponseEntity<?> busy(RejectedExecutionException error, ServerWebExchange exchange) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "Files are busy; retry later", exchange);
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
