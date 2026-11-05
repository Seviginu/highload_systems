package itmo.tracker.application.model;

public record TaskStatusChange(
        long taskId,
        long version,
        String taskKey,
        long actorId,
        long recipientId,
        String previousStatus,
        String status) {}
