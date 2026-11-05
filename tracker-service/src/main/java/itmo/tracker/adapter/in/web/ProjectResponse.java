package itmo.tracker.adapter.in.web;

import itmo.tracker.domain.ProjectStatus;

import java.time.Instant;

public record ProjectResponse(
        Long id,
        String name,
        String code,
        String description,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt) {}
