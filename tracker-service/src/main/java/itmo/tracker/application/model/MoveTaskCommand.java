package itmo.tracker.application.model;

import java.util.Set;

public record MoveTaskCommand(Long projectId, Long assigneeId, Set<Long> labelIds, Long version) {}
