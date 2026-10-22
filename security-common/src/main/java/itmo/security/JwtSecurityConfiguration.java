package itmo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;

import java.io.IOException;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class JwtSecurityConfiguration {
    @Bean
    public NimbusReactiveJwtDecoder jwtDecoder(@Value("${app.jwt.public-key}") Resource key,
                                               @Value("${app.jwt.issuer}") String issuer,
                                               @Value("${app.jwt.audience}") String audience) throws IOException {
        NimbusReactiveJwtDecoder decoder;
        try (var stream = key.getInputStream()) {
            decoder = NimbusReactiveJwtDecoder.withPublicKey(RsaKeyConverters.x509().convert(stream))
                    .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), jwt -> {
                    boolean valid;
                    try {
                        List<String> roles = jwt.getClaimAsStringList("roles");
                        valid = jwt.getExpiresAt() != null && jwt.getAudience().contains(audience)
                                && Long.parseLong(jwt.getSubject()) > 0 && roles != null && !roles.isEmpty()
                                && roles.stream().allMatch(List.of("ADMIN", "TEAM_LEAD", "DEVELOPER")::contains);
                    } catch (RuntimeException error) {
                        valid = false;
                    }
                    return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                            new OAuth2Error("invalid_token", "Invalid JWT claims", null));
                }));
        return decoder;
    }

    @Bean
    public ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    @Bean
    public SecurityErrors securityErrors(ObjectMapper mapper) {
        return new SecurityErrors(mapper);
    }
}
