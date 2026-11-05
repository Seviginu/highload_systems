package itmo.events.task;

import static org.assertj.core.api.Assertions.*;

import itmo.events.project.ProjectMemberAddedEvent;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

class TrackerEventsTest {
    @Test
    void statusContract() {
        var e =
                new TaskStatusChangedEvent(
                        UUID.randomUUID(),
                        TaskStatusChangedEvent.TYPE,
                        1,
                        Instant.now(),
                        1L,
                        2L,
                        3L,
                        List.of(4L, 4L),
                        new TaskStatusChangedEvent.Payload("T-1", "TODO", "DONE"));
        assertThat(e.recipientIds()).containsExactly(4L);
        assertThat(e.payload().status()).isEqualTo("DONE");
        assertThatThrownBy(
                        () ->
                                new TaskStatusChangedEvent(
                                        e.eventId(),
                                        e.eventType(),
                                        2,
                                        e.occurredAt(),
                                        e.aggregateId(),
                                        e.aggregateVersion(),
                                        e.actorId(),
                                        e.recipientIds(),
                                        e.payload()))
                .isInstanceOf(IllegalArgumentException.class);
        for (String status : Arrays.asList(null, "UNKNOWN", "TODO")) {
            assertThatThrownBy(() -> new TaskStatusChangedEvent.Payload("T-1", "TODO", status))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void membershipContract() {
        var e =
                new ProjectMemberAddedEvent(
                        UUID.randomUUID(),
                        ProjectMemberAddedEvent.TYPE,
                        1,
                        Instant.now(),
                        1L,
                        null,
                        3L,
                        List.of(4L, 4L),
                        new ProjectMemberAddedEvent.Payload(5L, "Project"));
        assertThat(e.recipientIds()).containsExactly(4L);
        assertThat(e.payload().memberId()).isEqualTo(5);
        assertThatThrownBy(
                        () ->
                                new ProjectMemberAddedEvent(
                                        e.eventId(),
                                        e.eventType(),
                                        2,
                                        e.occurredAt(),
                                        e.aggregateId(),
                                        null,
                                        e.actorId(),
                                        e.recipientIds(),
                                        e.payload()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectMemberAddedEvent.Payload(0L, "Project"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectMemberAddedEvent.Payload(1L, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
