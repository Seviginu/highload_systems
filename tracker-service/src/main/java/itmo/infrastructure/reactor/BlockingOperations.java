package itmo.infrastructure.reactor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.concurrent.Callable;

/** Keeps synchronous service proxies, their transactions and cache access on a worker. */
@Component
public class BlockingOperations {
    private final Scheduler scheduler;

    public BlockingOperations(@Qualifier("trackerBlockingScheduler") Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public <T> Mono<T> call(Callable<T> operation) {
        return Mono.fromCallable(operation).subscribeOn(scheduler);
    }
}
