package itmo.notification.application;

import java.util.*;

public record FileUploadedReceived(UUID eventId, UUID fileId, List<Long> recipients) {
    public FileUploadedReceived {
        recipients = List.copyOf(recipients);
    }
}
