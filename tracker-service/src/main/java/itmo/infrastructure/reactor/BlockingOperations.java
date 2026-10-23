package itmo.infrastructure.reactor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.security.concurrent.DelegatingSecurityContextCallable;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.concurrent.Callable;

/** Runs synchronous service proxies on a worker with a scoped security context. */
@Component
public class BlockingOperations {
    private final Scheduler scheduler;

    public BlockingOperations(@Qualifier("trackerBlockingScheduler") Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public <T> Mono<T> call(Callable<T> operation) {
        return ReactiveSecurityContextHolder.getContext()
                .defaultIfEmpty(new SecurityContextImpl())
                .flatMap(context -> Mono.fromCallable(new DelegatingSecurityContextCallable<>(operation, context))
                        .subscribeOn(scheduler));
    }

    public Mono<Void> run(Runnable operation) {
        return this.<Void>call(() -> {
            operation.run();
            return null;
        });
    }
}
