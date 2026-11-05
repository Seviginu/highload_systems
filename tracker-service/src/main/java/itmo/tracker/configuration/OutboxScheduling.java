package itmo.tracker.configuration;

import itmo.tracker.adapter.out.kafka.OutboxPublisher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduling {
    private final OutboxPublisher publisher;

    public OutboxScheduling(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:1000}")
    public void publish() {
        publisher.publishBatch();
    }
}
