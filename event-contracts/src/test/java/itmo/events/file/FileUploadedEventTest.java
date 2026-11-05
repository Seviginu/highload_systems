package itmo.events.file;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

class FileUploadedEventTest {
    @Test
    void shouldKeepUuidAndValidateSchemaAndSize() {
        var file = UUID.randomUUID();
        var e =
                new FileUploadedEvent(
                        UUID.randomUUID(),
                        FileUploadedEvent.TYPE,
                        1,
                        Instant.now(),
                        file,
                        null,
                        2L,
                        List.of(2L, 2L),
                        new FileUploadedEvent.Payload(1));
        assertThat(e.aggregateId()).isEqualTo(file);
        assertThat(e.recipientIds()).containsExactly(2L);
        assertThatThrownBy(
                        () ->
                                new FileUploadedEvent(
                                        e.eventId(),
                                        e.eventType(),
                                        2,
                                        e.occurredAt(),
                                        file,
                                        null,
                                        2L,
                                        List.of(2L),
                                        e.payload()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FileUploadedEvent.Payload(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
