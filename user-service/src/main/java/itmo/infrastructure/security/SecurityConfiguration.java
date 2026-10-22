package itmo.infrastructure.security;

import itmo.security.JwtSecurityConfiguration;
import itmo.security.SecurityErrors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

@Configuration(proxyBeanMethods = false)
@Import(JwtSecurityConfiguration.class)
@org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
public class SecurityConfiguration {
    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, SecurityErrors errors,
                                                  ReactiveJwtAuthenticationConverterAdapter converter) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .exceptionHandling(handlers -> handlers.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .authorizeExchange(exchanges -> exchanges
                    .pathMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                    .pathMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
                            "/webjars/swagger-ui/**", "/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness").permitAll()
                    .pathMatchers("/api/v1/users/**").hasRole("ADMIN")
                    .pathMatchers("/api/v1/**", "/actuator/info", "/internal/users/resolve").authenticated()
                    .anyExchange().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }
}
