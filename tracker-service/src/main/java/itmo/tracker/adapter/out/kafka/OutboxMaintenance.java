package itmo.tracker.adapter.out.kafka;

import itmo.tracker.adapter.out.persistence.OutboxRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

@Component
public class OutboxMaintenance {
    private final OutboxRepository outbox;
    private final Clock clock;
    private final int days;
    private final int batch;

    public OutboxMaintenance(
            OutboxRepository outbox,
            Clock clock,
            @Value("${app.outbox.retention-days:7}") int days,
            @Value("${app.outbox.cleanup-batch:1000}") int batch) {
        if (days < 1 || batch < 1 || batch > 10000)
            throw new IllegalArgumentException("Invalid outbox retention");
        this.outbox = outbox;
        this.clock = clock;
        this.days = days;
        this.batch = batch;
    }

    public int cleanupPublished() {
        return outbox.deletePublishedBefore(clock.instant().minus(days, ChronoUnit.DAYS), batch);
    }
}
