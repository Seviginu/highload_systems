package itmo.tracker.application.model;

import itmo.tracker.domain.ProjectStatus;

public record UpdateProjectCommand(
        String name, String code, String description, ProjectStatus status) {}
