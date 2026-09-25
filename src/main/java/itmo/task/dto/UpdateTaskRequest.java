package itmo.task.dto;

import itmo.task.entity.TaskPriority;
import itmo.task.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateTaskRequest(
        @NotBlank(message = "Task key must not be blank")
        @Size(max = 32, message = "Task key must not exceed 32 characters")
        @Pattern(
                regexp = "^[A-Za-z]\\w{1,19}-[1-9]\\d*$",
                message = "Task key must have format PROJECT-1"
        )
        String taskKey,

        @NotBlank(message = "Title must not be blank")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        String description,

        @NotNull(message = "Status is required")
        TaskStatus status,

        @NotNull(message = "Priority is required")
        TaskPriority priority,

        @NotNull(message = "Author is required")
        @Positive(message = "Author id must be positive")
        Long authorId,

        @Positive(message = "Assignee id must be positive")
        Long assigneeId,

        @NotNull(message = "Project is required")
        @Positive(message = "Project id must be positive")
        Long projectId,

        @Size(max = 50, message = "A task cannot have more than 50 labels")
        Set<@Positive(message = "Label id must be positive") Long> labelIds,

        @NotNull(message = "Version is required")
        @PositiveOrZero(message = "Version must not be negative")
        Long version
) {
}
