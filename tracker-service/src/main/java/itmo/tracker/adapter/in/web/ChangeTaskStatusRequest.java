package itmo.tracker.adapter.in.web;

import itmo.tracker.domain.TaskStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ChangeTaskStatusRequest(
        @NotNull TaskStatus status, @NotNull @PositiveOrZero Long version) {}
