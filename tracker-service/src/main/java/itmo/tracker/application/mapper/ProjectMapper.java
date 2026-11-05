package itmo.tracker.application.mapper;

import itmo.tracker.application.model.CreateProjectCommand;
import itmo.tracker.application.model.ProjectView;
import itmo.tracker.application.model.UpdateProjectCommand;
import itmo.tracker.domain.Project;

public class ProjectMapper {

    public Project toDomain(CreateProjectCommand request) {
        return new Project(
                normalizeName(request.name()),
                normalizeCode(request.code()),
                normalizeDescription(request.description()),
                request.status());
    }

    public void updateDomain(Project project, UpdateProjectCommand request) {
        project.update(
                normalizeName(request.name()),
                normalizeCode(request.code()),
                normalizeDescription(request.description()),
                request.status());
    }

    public ProjectView toResponse(Project project) {
        return new ProjectView(
                project.getId(),
                project.getName(),
                project.getCode(),
                project.getDescription(),
                project.getStatus(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    public String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
