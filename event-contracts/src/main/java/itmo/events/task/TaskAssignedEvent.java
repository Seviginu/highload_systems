package itmo.events.task;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskAssignedEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        Long aggregateId,
        Long aggregateVersion,
        Long actorId,
        List<Long> recipientIds,
        Payload payload) {
    public static final String TOPIC = "tracker.task.events.v1";
    public static final String TYPE = "TaskAssigned";

    public TaskAssignedEvent {
        if (eventId == null
                || !TYPE.equals(eventType)
                || schemaVersion != 1
                || occurredAt == null
                || aggregateId == null
                || aggregateId <= 0
                || aggregateVersion == null
                || aggregateVersion < 0
                || actorId == null
                || actorId <= 0
                || recipientIds == null
                || recipientIds.isEmpty()
                || recipientIds.size() > 50
                || recipientIds.stream().anyMatch(id -> id == null || id <= 0)
                || payload == null) {
            throw new IllegalArgumentException("Invalid TaskAssigned event");
        }
        recipientIds = List.copyOf(recipientIds.stream().distinct().toList());
    }

    public record Payload(String taskKey, Long projectId) {
        public Payload {
            if (taskKey == null || taskKey.isBlank() || projectId == null || projectId <= 0) {
                throw new IllegalArgumentException("Invalid TaskAssigned payload");
            }
        }
    }
}
