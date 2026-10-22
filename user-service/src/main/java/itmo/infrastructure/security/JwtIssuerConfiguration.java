package itmo.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;

@Configuration(proxyBeanMethods = false)
public class JwtIssuerConfiguration {
    @Bean
    JwtEncoder jwtEncoder(@Value("${app.jwt.public-key}") Resource publicKey,
                          @Value("${app.jwt.private-key}") Resource privateKey) throws IOException {
        try (var publicStream = publicKey.getInputStream(); var privateStream = privateKey.getInputStream()) {
            var key = new RSAKey.Builder(RsaKeyConverters.x509().convert(publicStream))
                    .privateKey(RsaKeyConverters.pkcs8().convert(privateStream)).build();
            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        }
    }
}
