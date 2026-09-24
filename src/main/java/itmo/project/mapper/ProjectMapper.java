package itmo.project.mapper;

import itmo.project.dto.CreateProjectRequest;
import itmo.project.dto.ProjectResponse;
import itmo.project.dto.UpdateProjectRequest;
import itmo.project.entity.Project;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class ProjectMapper {

    public Project toEntity(CreateProjectRequest request) {
        return new Project(
                normalizeName(request.name()),
                normalizeCode(request.code()),
                normalizeDescription(request.description()),
                request.status()
        );
    }

    public void updateEntity(Project project, UpdateProjectRequest request) {
        project.update(
                normalizeName(request.name()),
                normalizeCode(request.code()),
                normalizeDescription(request.description()),
                request.status()
        );
    }

    public ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getCode(),
                project.getDescription(),
                project.getStatus(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    public String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
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
