package itmo.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;
import java.util.List;

/** Publicly committed test-only credentials. Never used by application configuration. */
public final class TestTokens {
    public static final String ISSUER = "http://task-tracker";
    public static final String AUDIENCE = "task-tracker-api";
    private static final NimbusJwtEncoder ENCODER = encoder();

    private TestTokens() { }

    public static JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder().issuer(ISSUER).audience(List.of(AUDIENCE)).subject("1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).claim("roles", List.of("ADMIN"));
    }

    public static String token(JwtClaimsSet claims) {
        return ENCODER.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    public static String bearer() {
        return "Bearer " + token(claims().build());
    }

    public static String bearer(long id, String role) {
        return "Bearer " + token(claims().subject(Long.toString(id)).claim("roles", List.of(role)).build());
    }

    private static NimbusJwtEncoder encoder() {
        try (var publicKey = new ClassPathResource("keys/test-public.pem").getInputStream();
             var privateKey = new ClassPathResource("keys/test-private.pem").getInputStream()) {
            var key = new RSAKey.Builder(RsaKeyConverters.x509().convert(publicKey))
                    .privateKey(RsaKeyConverters.pkcs8().convert(privateKey)).build();
            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
