package itmo.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record Notification(
        UUID id,
        UUID eventId,
        long recipientId,
        String type,
        String message,
        String resourceType,
        String resourceId,
        Instant createdAt,
        Instant readAt) {}
