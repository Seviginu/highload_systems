package itmo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecurityConfigurationTest {
    private final JwtSecurityConfiguration configuration = new JwtSecurityConfiguration();

    @Test
    void shouldValidateSignatureClaimsAndConvertRoles() throws Exception {
        var decoder = configuration.jwtDecoder(new ClassPathResource("keys/test-public.pem"), TestTokens.ISSUER, TestTokens.AUDIENCE);
        Jwt jwt = decoder.decode(TestTokens.token(TestTokens.claims().build())).block();
        var authentication = configuration.jwtAuthenticationConverter().convert(jwt).block();
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        var invalid = List.of(
                TestTokens.claims().issuer("wrong").build(),
                TestTokens.claims().audience(List.of("wrong")).build(),
                TestTokens.claims().issuedAt(Instant.now().minusSeconds(600)).expiresAt(Instant.now().minusSeconds(120)).build(),
                TestTokens.claims().notBefore(Instant.now().plusSeconds(120)).build(),
                TestTokens.claims().subject("not-an-id").build(),
                TestTokens.claims().subject("0").build(),
                TestTokens.claims().claim("roles", List.of("UNKNOWN")).build(),
                TestTokens.claims().claim("roles", List.of()).build(),
                TestTokens.claims().claims(claims -> claims.remove("roles")).build(),
                TestTokens.claims().claims(claims -> claims.remove("exp")).build());
        for (var claims : invalid) {
            StepVerifier.create(decoder.decode(TestTokens.token(claims))).expectError(JwtException.class).verify();
        }
        String token = TestTokens.token(TestTokens.claims().build());
        int signature = token.lastIndexOf('.') + 1;
        String tampered = token.substring(0, signature) + (token.charAt(signature) == 'A' ? 'B' : 'A') + token.substring(signature + 1);
        StepVerifier.create(decoder.decode(tampered)).expectError(JwtException.class).verify();
    }

    @Test
    void shouldReturnConsistentUnauthorizedAndForbiddenJson() {
        var errors = configuration.securityErrors(new ObjectMapper());
        var anonymous = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/tasks"));
        errors.commence(anonymous, new BadCredentialsException("secret detail")).block();
        assertThat(anonymous.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(anonymous.getResponse().getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(anonymous.getResponse().getBodyAsString().block())
                .contains("\"status\":401", "\"path\":\"/api/v1/tasks\"").doesNotContain("secret detail");
        var forbidden = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users"));
        errors.handle(forbidden, new AccessDeniedException("secret detail")).block();
        assertThat(forbidden.getResponse().getStatusCode().value()).isEqualTo(403);
        assertThat(forbidden.getResponse().getBodyAsString().block()).contains("\"status\":403").doesNotContain("secret detail");
    }
}
