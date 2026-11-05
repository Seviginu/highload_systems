package itmo.tracker.adapter.out.kafka;

import itmo.tracker.adapter.out.persistence.OutboxRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final int batchSize;

    public OutboxPublisher(
            OutboxRepository outbox,
            KafkaTemplate<String, String> kafka,
            Clock clock,
            @Value("${app.outbox.batch-size:50}") int batchSize) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("Invalid outbox batch size");
        }
        this.outbox = outbox;
        this.kafka = kafka;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    // One publisher instance per service. Stop on failure to preserve aggregate order.
    public synchronized void publishBatch() {
        for (var event : outbox.pending(PageRequest.of(0, batchSize))) {
            if (event.getNextAttemptAt().isAfter(clock.instant())) {
                return;
            }
            try {
                kafka.send(event.getTopic(), event.getAggregateKey(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);
                outbox.published(event.getId(), clock.instant());
            } catch (Exception error) {
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                outbox.failed(
                        event.getId(),
                        clock.instant().plusSeconds(5),
                        error.getClass().getSimpleName());
                log.warn(
                        "Outbox event {} deferred ({})",
                        event.getEventId(),
                        error.getClass().getSimpleName());
                return;
            }
        }
    }
}
