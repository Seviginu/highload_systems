package itmo.infrastructure.reactor;

import itmo.common.exception.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingOperationsTest {
    private final Scheduler blocking = Schedulers.newBoundedElastic(1, 1, "tracker-blocking-test");
    private final Scheduler eventLoop = Schedulers.newSingle("http-test", true);
    private final BlockingOperations operations = new BlockingOperations(blocking);

    @AfterEach
    void disposeSchedulers() {
        blocking.dispose();
        eventLoop.dispose();
    }

    @Test
    void shouldBeLazyAndLeaveNonBlockingThread() {
        AtomicInteger calls = new AtomicInteger();
        Mono<String> result = operations.call(() -> {
            calls.incrementAndGet();
            assertThat(Schedulers.isInNonBlockingThread()).isFalse();
            return Thread.currentThread().getName();
        });
        assertThat(calls).hasValue(0);
        StepVerifier.create(Mono.defer(() -> {
                    assertThat(Schedulers.isInNonBlockingThread()).isTrue();
                    return result;
                }).subscribeOn(eventLoop))
                .expectNextMatches(name -> name.startsWith("tracker-blocking-test-"))
                .expectComplete().verify(Duration.ofSeconds(3));
        assertThat(calls).hasValue(1);
    }

    @Test
    void shouldPropagateBusinessException() {
        ConflictException conflict = new ConflictException("Version conflict");
        StepVerifier.create(operations.call(() -> { throw conflict; }))
                .expectErrorMatches(error -> error == conflict).verify(Duration.ofSeconds(3));
    }

    @Test
    void shouldRejectExcessWorkWhenQueueIsFull() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Disposable running = operations.call(() -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return "running";
        }).subscribe();
        Disposable queued = null;
        try {
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            queued = operations.call(() -> "queued").subscribe();
            StepVerifier.create(operations.call(() -> "excess"))
                    .expectError(RejectedExecutionException.class).verify(Duration.ofSeconds(3));
        } finally {
            release.countDown();
            running.dispose();
            if (queued != null) { queued.dispose(); }
        }
    }
}
