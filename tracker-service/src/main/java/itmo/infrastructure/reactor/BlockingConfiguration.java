package itmo.infrastructure.reactor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class BlockingConfiguration {
    @Bean(destroyMethod = "dispose")
    Scheduler trackerBlockingScheduler(
            @Value("${app.blocking.threads}") int threads,
            @Value("${app.blocking.queued-tasks}") int queuedTasks
    ) {
        return Schedulers.newBoundedElastic(threads, queuedTasks, "tracker-blocking");
    }
}
