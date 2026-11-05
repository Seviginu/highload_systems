package itmo.tracker.adapter.in.web;

import java.time.Instant;

public record ProjectMemberResponse(
        Long id, Long projectId, Long userId, Instant joinedAt, boolean active) {}
