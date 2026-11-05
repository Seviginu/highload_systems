package itmo.notification.application;

import itmo.notification.application.port.*;
import itmo.notification.domain.Notification;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

public class NotificationService {
    private final Notifications notifications;
    private final EventInbox inbox;
    private final UnitOfWork transactions;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public NotificationService(
            Notifications notifications,
            EventInbox inbox,
            UnitOfWork transactions,
            Clock clock,
            Supplier<UUID> ids) {
        this.notifications = notifications;
        this.inbox = inbox;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
    }

    public NotificationPage page(long recipientId, int page, int size) {
        if (recipientId <= 0 || page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException("Invalid notification page");
        }
        return transactions.read(
                () ->
                        new NotificationPage(
                                notifications.page(recipientId, page, size),
                                notifications.count(recipientId)));
    }

    public void markRead(UUID id, long recipientId) {
        transactions.write(
                () -> {
                    if (!notifications.markRead(id, recipientId, clock.instant())) {
                        throw new NotificationNotFound();
                    }
                    return null;
                });
    }

    public void receive(AssignmentReceived event) {
        store(
                event.eventId(),
                "TaskAssigned",
                "Task",
                Long.toString(event.taskId()),
                "Task '" + event.taskKey() + "' has been assigned to you",
                event.recipients());
    }

    public void receive(StatusChangedReceived event) {
        store(
                event.eventId(),
                "TaskStatusChanged",
                "Task",
                Long.toString(event.taskId()),
                "Task '"
                        + event.taskKey()
                        + "' changed from "
                        + event.previousStatus()
                        + " to "
                        + event.status(),
                event.recipients());
    }

    public void receive(ProjectMemberReceived event) {
        store(
                event.eventId(),
                "ProjectMemberAdded",
                "Project",
                Long.toString(event.projectId()),
                "You have been added to project '" + event.projectName() + "'",
                event.recipients());
    }

    public void receive(FileUploadedReceived event) {
        store(
                event.eventId(),
                "FileUploaded",
                "File",
                event.fileId().toString(),
                "File uploaded successfully",
                event.recipients());
    }

    private void store(
            UUID eventId,
            String type,
            String resourceType,
            String resourceId,
            String message,
            java.util.List<Long> recipients) {
        var now = clock.instant();
        var created =
                recipients.stream()
                        .distinct()
                        .map(
                                recipient ->
                                        new Notification(
                                                ids.get(),
                                                eventId,
                                                recipient,
                                                type,
                                                message,
                                                resourceType,
                                                resourceId,
                                                now,
                                                null))
                        .toList();
        transactions.write(
                () -> {
                    inbox.save(eventId, created);
                    return null;
                });
    }
}
