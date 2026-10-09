package itmo.common.web;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException error, ServerWebExchange exchange) {
        return build(HttpStatus.NOT_FOUND, error.getMessage(), exchange, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException error, ServerWebExchange exchange) {
        return build(HttpStatus.CONFLICT, error.getMessage(), exchange, List.of());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiError> handleValidation(WebExchangeBindException error, ServerWebExchange exchange) {
        var fields = error.getBindingResult().getFieldErrors().stream()
                .map(field -> new FieldValidationError(field.getField(), field.getDefaultMessage())).toList();
        return build(HttpStatus.BAD_REQUEST, "Request validation failed", exchange, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException error, ServerWebExchange exchange) {
        var fields = error.getConstraintViolations().stream()
                .map(field -> new FieldValidationError(field.getPropertyPath().toString(), field.getMessage())).toList();
        return build(HttpStatus.BAD_REQUEST, "Request validation failed", exchange, fields);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiError> handleMalformedRequest(ServerWebInputException error, ServerWebExchange exchange) {
        return build(HttpStatus.BAD_REQUEST, "Request body or parameter is malformed", exchange, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, ServerWebExchange exchange,
                                          List<FieldValidationError> fields) {
        var body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message,
                exchange.getRequest().getPath().value(), fields);
        return ResponseEntity.status(status).body(body);
    }
}
