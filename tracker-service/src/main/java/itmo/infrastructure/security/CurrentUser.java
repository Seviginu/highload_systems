package itmo.infrastructure.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

import java.util.Set;

/** Captured from the verified reactive security context before entering a blocking worker. */
public record CurrentUser(long id, Set<String> roles) {
    public CurrentUser {
        roles = Set.copyOf(roles);
    }

    public static Mono<CurrentUser> current() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication().getPrincipal())
                .ofType(Jwt.class)
                .map(jwt -> new CurrentUser(Long.parseLong(jwt.getSubject()),
                        Set.copyOf(jwt.getClaimAsStringList("roles"))))
                .switchIfEmpty(Mono.error(new AccessDeniedException("Authentication is required")));
    }

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }

    public boolean isTeamLead() {
        return roles.contains("TEAM_LEAD");
    }
}
