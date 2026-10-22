package itmo.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import itmo.infrastructure.security.PasswordConfiguration;
import itmo.user.dto.CreateUserRequest;
import itmo.user.entity.User;
import itmo.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordServiceTest {
    @Test
    void shouldHashLazilyOnPasswordWorkerAndUseRandomSalt() {
        var configuration = new PasswordConfiguration();
        var encoder = configuration.passwordEncoder();
        var thread = new AtomicReference<String>();
        var scheduler = configuration.passwordScheduler();
        try {
            var service = new PasswordService(encoder, scheduler);
            Mono<String> encoded = service.encode("Test-password-123").doOnNext(hash -> thread.set(Thread.currentThread().getName()));
            assertThat(thread.get()).isNull();
            var first = encoded.block();
            var second = encoded.block();
            assertThat(thread.get()).startsWith("password-worker-");
            assertThat(first).isNotEqualTo(second);
            assertThat(encoder.matches("Test-password-123", first)).isTrue();
            assertThat(encoder.matches("wrong-password", first)).isFalse();
            assertThat(service.encode("я".repeat(36)).block()).hasSize(60);
            StepVerifier.create(service.encode("я".repeat(37))).expectError(IllegalArgumentException.class).verify();
            StepVerifier.create(service.encode(null)).expectError(IllegalArgumentException.class).verify();
        } finally {
            scheduler.dispose();
        }
    }

    @Test
    void shouldRedactRequestToStringAndHideEntityHash() throws Exception {
        var request = new CreateUserRequest("Alice", "alice@example.com", UserRole.DEVELOPER, "Secret-password-123");
        assertThat(request.toString()).doesNotContain(request.password()).contains("<redacted>");
        var user = new User("Alice", "alice@example.com", UserRole.DEVELOPER);
        user.changePasswordHash(new BCryptPasswordEncoder(4).encode(request.password()));
        assertThat(new ObjectMapper().writeValueAsString(user))
                .doesNotContain("password", user.getPasswordHash(), request.password());
    }
}
