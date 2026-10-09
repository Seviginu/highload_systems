package itmo.project.dto;

import itmo.project.entity.ProjectStatus;

import java.time.Instant;

public record ProjectResponse(
        Long id,
        String name,
        String code,
        String description,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
