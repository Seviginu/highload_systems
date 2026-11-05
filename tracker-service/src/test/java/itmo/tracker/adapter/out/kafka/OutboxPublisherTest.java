package itmo.tracker.adapter.out.kafka;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import itmo.tracker.adapter.out.persistence.*;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

class OutboxPublisherTest {
    final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    final OutboxRepository outbox = mock(OutboxRepository.class);

    @SuppressWarnings("unchecked")
    final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

    final OutboxPublisher publisher =
            new OutboxPublisher(outbox, kafka, Clock.fixed(now, ZoneOffset.UTC), 50);

    OutboxEntity event(long id, Instant time) {
        var e = new OutboxEntity(UUID.randomUUID(), "topic", Long.toString(id), "{}", time);
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    @Test
    void shouldMarkPublishedOnlyAfterKafkaAcknowledgement() {
        when(outbox.pending(any())).thenReturn(List.of(event(1, now)));
        when(kafka.send("topic", "1", "{}")).thenReturn(CompletableFuture.completedFuture(null));
        publisher.publishBatch();
        var order = inOrder(kafka, outbox);
        order.verify(kafka).send("topic", "1", "{}");
        order.verify(outbox).published(1, now);
        verify(outbox, never()).failed(anyLong(), any(), any());
    }

    @Test
    void shouldDeferFailureAndPreserveOrder() {
        when(outbox.pending(any())).thenReturn(List.of(event(1, now), event(2, now)));
        when(kafka.send("topic", "1", "{}"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        publisher.publishBatch();
        verify(outbox).failed(1, now.plusSeconds(5), "ExecutionException");
        verify(outbox, never()).published(anyLong(), any());
        verify(kafka, never()).send(eq("topic"), eq("2"), anyString());
    }

    @Test
    void shouldNotSkipDeferredHeadRecord() {
        when(outbox.pending(any()))
                .thenReturn(List.of(event(1, now.plusSeconds(1)), event(2, now)));
        publisher.publishBatch();
        verifyNoInteractions(kafka);
    }

    @Test
    void shouldRepublishSameEventAfterAcknowledgementButFailedDatabaseUpdate() {
        var event = event(1, now);
        when(outbox.pending(any())).thenReturn(List.of(event));
        when(kafka.send("topic", "1", "{}")).thenReturn(CompletableFuture.completedFuture(null));
        when(outbox.published(1, now))
                .thenThrow(new IllegalStateException("Database unavailable"))
                .thenReturn(1);
        publisher.publishBatch();
        publisher.publishBatch();
        verify(kafka, times(2)).send("topic", "1", "{}");
        verify(outbox).failed(1, now.plusSeconds(5), "IllegalStateException");
        verify(outbox, times(2)).published(1, now);
    }

    @Test
    void shouldRejectUnboundedBatches() {
        assertThatThrownBy(() -> new OutboxPublisher(outbox, kafka, Clock.systemUTC(), 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
