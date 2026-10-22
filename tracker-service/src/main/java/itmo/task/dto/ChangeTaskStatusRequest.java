package itmo.task.dto;

import itmo.task.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ChangeTaskStatusRequest(@NotNull TaskStatus status, @NotNull @PositiveOrZero Long version) {
}
