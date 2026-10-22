package itmo.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class SecurityErrors implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {
    private final ObjectMapper mapper;

    public SecurityErrors(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException error) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return write(exchange, HttpStatus.UNAUTHORIZED, "Authentication is required or token is invalid");
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException error) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"");
        return write(exchange, HttpStatus.FORBIDDEN, "Access denied");
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message) {
        return Mono.defer(() -> {
            var response = exchange.getResponse();
            response.setStatusCode(status);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            try {
                byte[] bytes = mapper.writeValueAsBytes(Map.of("timestamp", Instant.now().toString(),
                        "status", status.value(), "error", status.getReasonPhrase(), "message", message,
                        "path", exchange.getRequest().getPath().value(), "fieldErrors", List.of()));
                return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
            } catch (JsonProcessingException error) {
                return Mono.error(error);
            }
        });
    }
}
