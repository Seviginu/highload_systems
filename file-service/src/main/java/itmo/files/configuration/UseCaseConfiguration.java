package itmo.files.configuration;

import itmo.files.application.*;
import itmo.files.application.port.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

import reactor.core.scheduler.*;

import java.time.Clock;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {
    @Bean
    Clock fileClock() {
        return Clock.systemUTC();
    }

    @Bean
    FileService files(
            Files files,
            FileEvents events,
            UnitOfWork transactions,
            Clock clock,
            @Value("${app.files.max-file-bytes:10485760}") int max) {
        return new FileService(files, events, transactions, clock, UUID::randomUUID, max);
    }

    @Bean(destroyMethod = "dispose")
    Scheduler fileWorker() {
        return Schedulers.newBoundedElastic(4, 100, "file-blocking");
    }
}
