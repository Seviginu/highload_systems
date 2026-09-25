package itmo.task.dto;

import itmo.task.entity.TaskPriority;
import itmo.task.entity.TaskStatus;

public record TaskFilter(
        Long projectId,
        Long authorId,
        Long assigneeId,
        Long labelId,
        TaskStatus status,
        TaskPriority priority
) {
}
