package itmo.tracker.application.model;

import itmo.tracker.domain.TaskPriority;
import itmo.tracker.domain.TaskStatus;

public record TaskFilter(
        Long projectId,
        Long authorId,
        Long assigneeId,
        Long labelId,
        TaskStatus status,
        TaskPriority priority) {}
