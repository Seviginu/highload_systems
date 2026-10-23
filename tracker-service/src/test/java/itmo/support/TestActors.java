package itmo.support;

import itmo.infrastructure.security.CurrentUser;
import itmo.infrastructure.security.TrackerAccess;
import itmo.project.repository.ProjectMemberRepository;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.WebFilter;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;

public final class TestActors {
    private TestActors() { }

    public static CurrentUser admin() {
        return new CurrentUser(2, Set.of("ADMIN"));
    }

    public static TrackerAccess access() {
        return new TrackerAccess(mock(ProjectMemberRepository.class));
    }

    /** Controller unit tests provide a verified principal; HTTP integration tests validate real JWTs. */
    public static WebFilter context() {
        var jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject("2")
                .claim("roles", List.of("ADMIN")).build();
        return (exchange, chain) -> chain.filter(exchange).contextWrite(
                ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt)));
    }
}
