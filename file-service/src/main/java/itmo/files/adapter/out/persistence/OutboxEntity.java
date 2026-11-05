package itmo.files.adapter.out.persistence;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
public class OutboxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    @NotNull
    private UUID eventId;

    @Column(nullable = false)
    @NotBlank
    @Size(max = 255)
    private String topic;

    @Column(nullable = false)
    @NotBlank
    @Size(max = 255)
    private String aggregateKey;

    @Column(nullable = false, columnDefinition = "text")
    @NotBlank
    private String payload;

    @Column(nullable = false)
    @NotNull
    private Instant createdAt;

    private Instant publishedAt;

    @Column(nullable = false)
    @PositiveOrZero
    private int attempts;

    @Column(nullable = false)
    @NotNull
    private Instant nextAttemptAt;

    private String lastError;

    protected OutboxEntity() {}

    public OutboxEntity(
            UUID eventId, String topic, String aggregateKey, String payload, Instant now) {
        this.eventId = eventId;
        this.topic = topic;
        this.aggregateKey = aggregateKey;
        this.payload = payload;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }
}
