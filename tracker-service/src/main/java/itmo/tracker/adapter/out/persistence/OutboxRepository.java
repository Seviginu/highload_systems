package itmo.tracker.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEntity, Long> {
    @Transactional(readOnly = true)
    @Query("select event from OutboxEntity event where event.publishedAt is null order by event.id")
    List<OutboxEntity> pending(Pageable page);

    @Transactional
    @Modifying
    @Query(
            "update OutboxEntity e set e.publishedAt=:now, e.attempts=e.attempts+1,"
                    + " e.lastError=null where e.id=:id and e.publishedAt is null")
    int published(@Param("id") long id, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query(
            "update OutboxEntity e set e.attempts=e.attempts+1, e.nextAttemptAt=:next,"
                    + " e.lastError=:error where e.id=:id and e.publishedAt is null")
    int failed(@Param("id") long id, @Param("next") Instant next, @Param("error") String error);

    @Transactional
    @Modifying
    @Query(
            value =
                    "DELETE FROM outbox_events WHERE id IN (SELECT id FROM outbox_events WHERE"
                        + " published_at < :before ORDER BY id LIMIT :batch)",
            nativeQuery = true)
    int deletePublishedBefore(@Param("before") Instant before, @Param("batch") int batch);
}
