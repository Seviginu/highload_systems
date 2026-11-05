package itmo.tracker.application.model;

public record ProjectMembership(
        long projectId, long memberId, String projectName, long actorId, long recipientId) {}
