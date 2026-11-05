package itmo.events.project;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectMemberAddedEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        Long aggregateId,
        Long aggregateVersion,
        Long actorId,
        List<Long> recipientIds,
        Payload payload) {
    public static final String TOPIC = "tracker.project.events.v1";
    public static final String TYPE = "ProjectMemberAdded";

    public ProjectMemberAddedEvent {
        if (eventId == null
                || !TYPE.equals(eventType)
                || schemaVersion != 1
                || occurredAt == null
                || aggregateId == null
                || aggregateId <= 0
                || (aggregateVersion != null && aggregateVersion < 0)
                || actorId == null
                || actorId <= 0
                || recipientIds == null
                || recipientIds.isEmpty()
                || recipientIds.size() > 50
                || recipientIds.stream().anyMatch(id -> id == null || id <= 0)
                || payload == null) {
            throw new IllegalArgumentException("Invalid ProjectMemberAdded event");
        }
        recipientIds = List.copyOf(recipientIds.stream().distinct().toList());
    }

    public record Payload(Long memberId, String projectName) {
        public Payload {
            if (memberId == null || memberId <= 0 || projectName == null || projectName.isBlank()) {
                throw new IllegalArgumentException("Invalid ProjectMemberAdded payload");
            }
        }
    }
}
