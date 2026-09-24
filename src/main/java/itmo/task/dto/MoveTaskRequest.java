package itmo.task.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record MoveTaskRequest(
        @NotNull(message = "Project is required")
        @Positive(message = "Project id must be positive")
        Long projectId,

        @Positive(message = "Assignee id must be positive")
        Long assigneeId,

        @NotNull(message = "Label ids are required")
        @Size(max = 50, message = "A task cannot have more than 50 labels")
        Set<@Positive(message = "Label id must be positive") Long> labelIds,

        @NotNull(message = "Version is required")
        @PositiveOrZero(message = "Version must not be negative")
        Long version
) {
}
