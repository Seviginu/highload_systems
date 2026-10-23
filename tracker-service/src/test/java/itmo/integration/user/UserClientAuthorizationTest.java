package itmo.integration.user;

import feign.RequestTemplate;
import itmo.infrastructure.reactor.BlockingOperations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class UserClientAuthorizationTest {
    private final Scheduler worker = Schedulers.newBoundedElastic(2, 10, "jwt-relay-test");
    private final BlockingOperations blocking = new BlockingOperations(worker);
    private final feign.RequestInterceptor interceptor = new UserClientConfiguration().userClientAuthorization();

    @AfterEach
    void cleanup() {
        worker.dispose();
        SecurityContextHolder.clearContext();
    }

    private String outgoingAuthorization() {
        var template = new RequestTemplate();
        template.header("Authorization", "Bearer stale");
        interceptor.apply(template);
        assertThat(template.headers().get("Authorization")).hasSize(1);
        return template.headers().get("Authorization").iterator().next();
    }

    private reactor.util.context.Context context(String token) {
        var jwt = Jwt.withTokenValue(token).header("alg", "RS256").subject("1").build();
        return ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @Test
    void shouldIsolateConcurrentRequestsAndRepeatedSubscriptions() {
        var entered = new CountDownLatch(2);
        Mono<String> request = blocking.call(() -> {
            entered.countDown();
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            return outgoingAuthorization();
        });
        StepVerifier.create(Mono.zip(request.contextWrite(context("alice")), request.contextWrite(context("bob"))))
                .assertNext(tokens -> {
                    assertThat(tokens.getT1()).isEqualTo("Bearer alice");
                    assertThat(tokens.getT2()).isEqualTo("Bearer bob");
                }).verifyComplete();
        StepVerifier.create(request.contextWrite(context("carol")))
                .expectNext("Bearer carol").verifyComplete();
        StepVerifier.create(blocking.call(this::outgoingAuthorization))
                .expectError(AuthenticationCredentialsNotFoundException.class).verify();
    }

    @Test
    void shouldRestorePreviousWorkerContextAfterSuccessAndFailure() {
        Scheduler single = Schedulers.newSingle("jwt-cleanup-test");
        try {
            var operations = new BlockingOperations(single);
            var previous = SecurityContextHolder.createEmptyContext();
            previous.setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken("previous", "unused"));
            Mono.fromRunnable(() -> SecurityContextHolder.setContext(previous)).subscribeOn(single).block();
            StepVerifier.create(operations.call(this::outgoingAuthorization).contextWrite(context("alice")))
                    .expectNext("Bearer alice").verifyComplete();
            assertThat(Mono.fromCallable(SecurityContextHolder::getContext).subscribeOn(single).block()).isSameAs(previous);
            StepVerifier.create(operations.run(() -> {
                        assertThat(outgoingAuthorization()).isEqualTo("Bearer bob");
                        throw new IllegalStateException("failure");
                    }).contextWrite(context("bob")))
                    .expectErrorMessage("failure").verify(Duration.ofSeconds(3));
            assertThat(Mono.fromCallable(SecurityContextHolder::getContext).subscribeOn(single).block()).isSameAs(previous);
            Mono.fromRunnable(SecurityContextHolder::clearContext).subscribeOn(single).block();
            StepVerifier.create(operations.run(() -> outgoingAuthorization()).contextWrite(context("carol")))
                    .verifyComplete();
            assertThat(Mono.fromCallable(() -> SecurityContextHolder.getContext().getAuthentication() == null)
                    .subscribeOn(single).block()).isTrue();
        } finally {
            single.dispose();
        }
    }
}
