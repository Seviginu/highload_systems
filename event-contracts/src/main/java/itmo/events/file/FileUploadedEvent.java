package itmo.events.file;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FileUploadedEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        UUID aggregateId,
        Long aggregateVersion,
        Long actorId,
        List<Long> recipientIds,
        Payload payload) {
    public static final String TOPIC = "files.events.v1";
    public static final String TYPE = "FileUploaded";

    public FileUploadedEvent {
        if (eventId == null
                || !TYPE.equals(eventType)
                || schemaVersion != 1
                || occurredAt == null
                || aggregateId == null
                || (aggregateVersion != null && aggregateVersion < 0)
                || actorId == null
                || actorId <= 0
                || recipientIds == null
                || recipientIds.isEmpty()
                || recipientIds.size() > 50
                || recipientIds.stream().anyMatch(id -> id == null || id <= 0)
                || payload == null) {
            throw new IllegalArgumentException("Invalid FileUploaded event");
        }
        recipientIds = List.copyOf(recipientIds.stream().distinct().toList());
    }

    public record Payload(long size) {
        public Payload {
            if (size <= 0) throw new IllegalArgumentException("Invalid FileUploaded payload");
        }
    }
}
