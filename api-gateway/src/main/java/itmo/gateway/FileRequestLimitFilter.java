package itmo.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.*;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;

import reactor.core.publisher.*;

import java.time.Instant;
import java.util.*;

@Component
@Order(-50)
public class FileRequestLimitFilter implements WebFilter {
    private final int max;
    private final ObjectMapper mapper;

    public FileRequestLimitFilter(
            @Value("${app.files.max-file-bytes:10485760}") int max, ObjectMapper mapper) {
        if (max < 1 || max > 50 * 1024 * 1024)
            throw new IllegalArgumentException("File limit must be 1..52428800 bytes");
        this.max = max + 65536;
        this.mapper = mapper;
    }

    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getMethod() != HttpMethod.POST
                || !exchange.getRequest().getPath().value().equals("/api/v1/files"))
            return chain.filter(exchange);
        if (exchange.getRequest().getHeaders().getContentLength() > max) return reject(exchange);
        return DataBufferUtils.join(exchange.getRequest().getBody(), max)
                .map(
                        buffer -> {
                            try {
                                var bytes = new byte[buffer.readableByteCount()];
                                buffer.read(bytes);
                                return bytes;
                            } finally {
                                DataBufferUtils.release(buffer);
                            }
                        })
                .defaultIfEmpty(new byte[0])
                .flatMap(
                        bytes -> {
                            var request =
                                    new ServerHttpRequestDecorator(exchange.getRequest()) {
                                        @Override
                                        public HttpHeaders getHeaders() {
                                            var headers = new HttpHeaders();
                                            headers.putAll(super.getHeaders());
                                            headers.remove(HttpHeaders.TRANSFER_ENCODING);
                                            headers.setContentLength(bytes.length);
                                            return headers;
                                        }

                                        @Override
                                        public Flux<DataBuffer> getBody() {
                                            return Flux.defer(
                                                    () ->
                                                            Flux.just(
                                                                    exchange.getResponse()
                                                                            .bufferFactory()
                                                                            .wrap(bytes)));
                                        }
                                    };
                            return chain.filter(exchange.mutate().request(request).build());
                        })
                .onErrorResume(DataBufferLimitException.class, error -> reject(exchange));
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            var bytes =
                    mapper.writeValueAsBytes(
                            Map.of(
                                    "timestamp",
                                    Instant.now().toString(),
                                    "status",
                                    413,
                                    "error",
                                    "Payload Too Large",
                                    "message",
                                    "Upload request exceeds the configured size limit",
                                    "path",
                                    exchange.getRequest().getPath().value(),
                                    "fieldErrors",
                                    List.of()));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}
