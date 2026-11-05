package itmo.events.task;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

class TaskAssignedEventTest {
    @Test
    void shouldKeepRecipientsImmutableAndDeduplicate() {
        var recipients = new ArrayList<>(List.of(2L, 2L));
        var event =
                new TaskAssignedEvent(
                        UUID.randomUUID(),
                        "TaskAssigned",
                        1,
                        Instant.now(),
                        1L,
                        0L,
                        1L,
                        recipients,
                        new TaskAssignedEvent.Payload("TASK-1", 1L));
        recipients.clear();
        assertThat(event.recipientIds()).containsExactly(2L);
        assertThatThrownBy(() -> event.recipientIds().add(3L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectUnsupportedSchemaAndInvalidPayload() {
        assertThatThrownBy(
                        () ->
                                new TaskAssignedEvent(
                                        UUID.randomUUID(),
                                        "TaskAssigned",
                                        2,
                                        Instant.now(),
                                        1L,
                                        0L,
                                        1L,
                                        List.of(2L),
                                        new TaskAssignedEvent.Payload("TASK-1", 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TaskAssignedEvent.Payload(" ", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
