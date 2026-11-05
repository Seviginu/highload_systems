package itmo.tracker.application.model;

import itmo.tracker.domain.TaskPriority;
import itmo.tracker.domain.TaskStatus;

import java.util.Set;

public record CreateTaskCommand(
        String taskKey,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        Long assigneeId,
        Long projectId,
        Set<Long> labelIds) {}
