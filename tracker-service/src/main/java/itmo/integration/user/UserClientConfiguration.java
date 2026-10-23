package itmo.integration.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/** Registered only in this Feign client's context, not in the WebFlux server. */
public class UserClientConfiguration {
    @Bean
    RequestInterceptor userClientAuthorization() {
        return template -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
                throw new AuthenticationCredentialsNotFoundException("JWT authentication is required");
            }
            template.removeHeader(HttpHeaders.AUTHORIZATION);
            template.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getToken().getTokenValue());
        };
    }

    @Bean
    HttpMessageConverters userClientMessageConverters(ObjectMapper objectMapper) {
        return new HttpMessageConverters(false, List.of(new MappingJackson2HttpMessageConverter(objectMapper)));
    }
}
