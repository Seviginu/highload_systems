package itmo.tracker.adapter.in.web;

import itmo.tracker.domain.TaskPriority;
import itmo.tracker.domain.TaskStatus;

import java.time.Instant;
import java.util.List;

public record TaskResponse(
        Long id,
        String taskKey,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        Long authorId,
        Long assigneeId,
        Long projectId,
        List<Long> labelIds,
        Long version,
        Instant createdAt,
        Instant updatedAt) {}
