package itmo.notification.adapter.out.persistence;

import itmo.notification.application.port.*;
import itmo.notification.domain.Notification;

import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JpaNotifications implements Notifications, EventInbox {
    private final NotificationRepository repository;
    private final JdbcTemplate jdbc;

    public JpaNotifications(NotificationRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    public List<Notification> page(long recipientId, int page, int size) {
        return repository
                .findByRecipientIdOrderByCreatedAtDescIdDesc(
                        recipientId, PageRequest.of(page, size))
                .stream()
                .map(NotificationEntity::toDomain)
                .toList();
    }

    public long count(long recipientId) {
        return repository.countByRecipientId(recipientId);
    }

    public boolean markRead(UUID id, long recipientId, Instant now) {
        return repository.markRead(id, recipientId, now) > 0;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(UUID eventId, List<Notification> notifications) {
        int inserted =
                jdbc.update(
                        "INSERT INTO processed_events(event_id,processed_at) VALUES (?,?) ON"
                                + " CONFLICT DO NOTHING",
                        eventId,
                        Timestamp.from(notifications.getFirst().createdAt()));
        if (inserted == 0) {
            return;
        }
        repository.saveAllAndFlush(notifications.stream().map(NotificationEntity::new).toList());
    }
}
