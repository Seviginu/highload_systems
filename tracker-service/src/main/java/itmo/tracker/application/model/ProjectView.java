package itmo.tracker.application.model;

import itmo.tracker.domain.ProjectStatus;

import java.time.Instant;

public record ProjectView(
        Long id,
        String name,
        String code,
        String description,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt) {}
