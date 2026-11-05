package itmo.notification.application;

import java.util.List;
import java.util.UUID;

public record StatusChangedReceived(
        UUID eventId,
        long taskId,
        String taskKey,
        String previousStatus,
        String status,
        List<Long> recipients) {
    public StatusChangedReceived {
        recipients = List.copyOf(recipients);
    }
}
