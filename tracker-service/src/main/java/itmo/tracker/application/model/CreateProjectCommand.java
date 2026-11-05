package itmo.tracker.application.model;

import itmo.tracker.domain.ProjectStatus;

public record CreateProjectCommand(
        String name, String code, String description, ProjectStatus status, Long teamLeadId) {}
