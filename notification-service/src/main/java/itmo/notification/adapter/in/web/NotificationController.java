package itmo.notification.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;

import itmo.notification.application.NotificationService;

import jakarta.validation.constraints.*;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.*;
import reactor.core.scheduler.Scheduler;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;
    private final Scheduler worker;

    public NotificationController(
            NotificationService service, @Qualifier("notificationWorker") Scheduler worker) {
        this.service = service;
        this.worker = worker;
    }

    @GetMapping(produces = "application/json")
    @Operation(summary = "Read your notifications; X-Total-Count contains the total")
    public Flux<NotificationResponse> page(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            ServerHttpResponse response) {
        long recipient = Long.parseLong(jwt.getSubject());
        return Mono.fromCallable(() -> service.page(recipient, page, size))
                .subscribeOn(worker)
                .flatMapMany(
                        result -> {
                            response.getHeaders()
                                    .set("X-Total-Count", Long.toString(result.total()));
                            return Flux.fromIterable(result.content())
                                    .map(NotificationResponse::from);
                        });
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark your notification as read")
    public Mono<Void> read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        long recipient = Long.parseLong(jwt.getSubject());
        return Mono.fromRunnable(() -> service.markRead(id, recipient)).subscribeOn(worker).then();
    }
}
