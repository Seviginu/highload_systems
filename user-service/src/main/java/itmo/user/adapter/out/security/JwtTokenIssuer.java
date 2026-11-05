package itmo.user.adapter.out.security;

import itmo.user.application.model.TokenResult;
import itmo.user.application.port.out.TokenIssuer;
import itmo.user.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;

@Component
public class JwtTokenIssuer implements TokenIssuer {
    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final long ttl;

    public JwtTokenIssuer(JwtEncoder encoder, @Value("${app.jwt.issuer}") String issuer,
                          @Value("${app.jwt.audience}") String audience,
                          @Value("${app.jwt.ttl-seconds:900}") long ttl) {
        if (ttl <= 0) { throw new IllegalArgumentException("JWT TTL must be positive"); }
        this.encoder = encoder;
        this.issuer = issuer;
        this.audience = audience;
        this.ttl = ttl;
    }

    @Override
    public TokenResult issue(User user) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
                .subject(user.getId().toString()).issuedAt(now).expiresAt(now.plusSeconds(ttl))
                .claim("roles", List.of(user.getRole().name())).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
        return new TokenResult(token, "Bearer", ttl);
    }
}
