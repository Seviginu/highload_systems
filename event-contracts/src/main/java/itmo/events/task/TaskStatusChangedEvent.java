package itmo.events.task;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskStatusChangedEvent(
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
    public static final String TYPE = "TaskStatusChanged";

    public TaskStatusChangedEvent {
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
            throw new IllegalArgumentException("Invalid TaskStatusChanged event");
        }
        recipientIds = List.copyOf(recipientIds.stream().distinct().toList());
    }

    public record Payload(String taskKey, String previousStatus, String status) {
        public Payload {
            var statuses = List.of("TODO", "IN_PROGRESS", "DONE");
            if (taskKey == null
                    || taskKey.isBlank()
                    || previousStatus == null
                    || status == null
                    || !statuses.contains(previousStatus)
                    || !statuses.contains(status)
                    || previousStatus.equals(status)) {
                throw new IllegalArgumentException("Invalid TaskStatusChanged payload");
            }
        }
    }
}
