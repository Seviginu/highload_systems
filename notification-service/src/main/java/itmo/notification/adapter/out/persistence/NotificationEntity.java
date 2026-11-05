package itmo.notification.adapter.out.persistence;

import itmo.notification.domain.Notification;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
public class NotificationEntity {
    @Id private UUID id;

    @Column(nullable = false)
    @NotNull
    private UUID eventId;

    @Column(nullable = false)
    @Positive
    private long recipientId;

    @Column(nullable = false)
    @NotBlank
    @Size(max = 255)
    private String type;

    @Column(nullable = false)
    @NotBlank
    @Size(max = 255)
    private String message;

    @Column(nullable = false)
    @NotBlank
    @Size(max = 255)
    private String resourceType;

    @Column(nullable = false)
    @NotBlank
    @Size(max = 255)
    private String resourceId;

    @Column(nullable = false)
    @NotNull
    private Instant createdAt;

    private Instant readAt;

    protected NotificationEntity() {}

    public NotificationEntity(Notification n) {
        id = n.id();
        eventId = n.eventId();
        recipientId = n.recipientId();
        type = n.type();
        message = n.message();
        resourceType = n.resourceType();
        resourceId = n.resourceId();
        createdAt = n.createdAt();
        readAt = n.readAt();
    }

    public Notification toDomain() {
        return new Notification(
                id,
                eventId,
                recipientId,
                type,
                message,
                resourceType,
                resourceId,
                createdAt,
                readAt);
    }
}
