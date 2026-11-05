package itmo.support;

import itmo.tracker.application.model.Actor;
import itmo.tracker.application.service.TrackerAccess;
import itmo.tracker.application.port.out.ProjectMemberStore;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.WebFilter;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;

public final class TestActors {
    private TestActors() { }

    public static Actor admin() {
        return new Actor(2, Set.of("ADMIN"));
    }

    public static TrackerAccess access() {
        return new TrackerAccess(mock(ProjectMemberStore.class));
    }

    /** Controller unit tests provide a verified principal; HTTP integration tests validate real JWTs. */
    public static WebFilter context() {
        var jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject("2")
                .claim("roles", List.of("ADMIN")).build();
        return (exchange, chain) -> chain.filter(exchange).contextWrite(
                ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt)));
    }
}
