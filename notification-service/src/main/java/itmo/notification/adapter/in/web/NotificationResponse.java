package itmo.notification.adapter.in.web;

import itmo.notification.domain.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID eventId,
        long recipientId,
        String type,
        String message,
        String resourceType,
        String resourceId,
        Instant createdAt,
        Instant readAt) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.id(),
                n.eventId(),
                n.recipientId(),
                n.type(),
                n.message(),
                n.resourceType(),
                n.resourceId(),
                n.createdAt(),
                n.readAt());
    }
}
