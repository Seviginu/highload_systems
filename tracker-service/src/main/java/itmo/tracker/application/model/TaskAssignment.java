package itmo.tracker.application.model;

public record TaskAssignment(
        long taskId,
        long version,
        String taskKey,
        long projectId,
        long actorId,
        long recipientId) {}
