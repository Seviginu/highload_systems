package itmo.tracker.adapter.in.web;

import itmo.tracker.application.model.Actor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import reactor.core.publisher.Mono;

import java.util.Set;

/** Captured from the verified reactive security context before entering a blocking worker. */
public final class CurrentUser {
    private CurrentUser() {}

    public static Mono<Actor> current() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication().getPrincipal())
                .ofType(Jwt.class)
                .map(
                        jwt ->
                                new Actor(
                                        Long.parseLong(jwt.getSubject()),
                                        Set.copyOf(jwt.getClaimAsStringList("roles"))))
                .switchIfEmpty(Mono.error(new AccessDeniedException("Authentication is required")));
    }
}
