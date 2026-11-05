package itmo.notification.application;

import java.util.List;
import java.util.UUID;

public record AssignmentReceived(UUID eventId, long taskId, String taskKey, List<Long> recipients) {
    public AssignmentReceived {
        recipients = List.copyOf(recipients);
    }
}
