package itmo.notification.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    List<NotificationEntity> findByRecipientIdOrderByCreatedAtDescIdDesc(
            long recipientId, Pageable page);

    long countByRecipientId(long recipientId);

    @Modifying
    @Query(
            "update NotificationEntity n set n.readAt=coalesce(n.readAt,:now) where n.id=:id and"
                    + " n.recipientId=:recipientId")
    int markRead(
            @Param("id") UUID id,
            @Param("recipientId") long recipientId,
            @Param("now") Instant now);
}
