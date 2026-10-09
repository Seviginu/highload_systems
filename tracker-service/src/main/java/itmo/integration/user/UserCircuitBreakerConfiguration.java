package itmo.integration.user;

import org.springframework.cloud.openfeign.CircuitBreakerNameResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UserCircuitBreakerConfiguration {
    @Bean
    CircuitBreakerNameResolver userCircuitBreakerNameResolver() {
        return (clientName, target, method) -> "userDirectory";
    }
}
