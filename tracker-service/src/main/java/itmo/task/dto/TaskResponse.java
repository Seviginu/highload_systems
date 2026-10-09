package itmo.task.dto;

import itmo.task.entity.TaskPriority;
import itmo.task.entity.TaskStatus;

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
        Instant updatedAt
) {
}
