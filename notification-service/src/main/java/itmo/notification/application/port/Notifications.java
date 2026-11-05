package itmo.notification.application.port;

import itmo.notification.domain.Notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface Notifications {
    List<Notification> page(long recipientId, int page, int size);

    long count(long recipientId);

    boolean markRead(UUID id, long recipientId, Instant now);
}
