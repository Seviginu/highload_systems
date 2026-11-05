package itmo.tracker.application.model;

import java.time.Instant;

public record ProjectMemberView(
        Long id, Long projectId, Long userId, Instant joinedAt, boolean active) {}
