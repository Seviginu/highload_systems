package itmo.notification.application.port;

import itmo.notification.domain.Notification;

import java.util.List;
import java.util.UUID;

public interface EventInbox {
    void save(UUID eventId, List<Notification> notifications);
}
