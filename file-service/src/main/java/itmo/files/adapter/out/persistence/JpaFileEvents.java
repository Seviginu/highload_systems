package itmo.files.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import itmo.events.file.FileUploadedEvent;
import itmo.files.application.port.FileEvents;
import itmo.files.domain.FileMetadata;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

import java.time.Clock;
import java.util.*;

@Component
public class JpaFileEvents implements FileEvents {
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final Clock clock;

    public JpaFileEvents(OutboxRepository outbox, ObjectMapper mapper, Clock clock) {
        this.outbox = outbox;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(FileMetadata file) {
        var id = UUID.randomUUID();
        var now = clock.instant();
        var event =
                new FileUploadedEvent(
                        id,
                        FileUploadedEvent.TYPE,
                        1,
                        now,
                        file.id(),
                        null,
                        file.ownerId(),
                        List.of(file.ownerId()),
                        new FileUploadedEvent.Payload(file.size()));
        try {
            outbox.saveAndFlush(
                    new OutboxEntity(
                            id,
                            FileUploadedEvent.TOPIC,
                            file.id().toString(),
                            mapper.writeValueAsString(event),
                            now));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot encode file event", e);
        }
    }
}
