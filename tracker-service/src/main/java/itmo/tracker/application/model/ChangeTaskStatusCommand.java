package itmo.tracker.application.model;

import itmo.tracker.domain.TaskStatus;

public record ChangeTaskStatusCommand(TaskStatus status, Long version) {}
