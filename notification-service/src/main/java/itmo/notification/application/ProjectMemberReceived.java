package itmo.notification.application;

import java.util.List;
import java.util.UUID;

public record ProjectMemberReceived(
        UUID eventId, long projectId, String projectName, List<Long> recipients) {
    public ProjectMemberReceived {
        recipients = List.copyOf(recipients);
    }
}
