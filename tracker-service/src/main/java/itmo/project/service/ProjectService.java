package itmo.project.service;

import itmo.infrastructure.security.CurrentUser;
import itmo.project.dto.CreateProjectRequest;
import itmo.project.dto.ProjectResponse;
import itmo.project.dto.UpdateProjectRequest;
import itmo.project.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProjectService {

    ProjectResponse create(CreateProjectRequest request, CurrentUser actor);

    ProjectResponse findById(Long id);

    Project requireEntity(Long id);

    Page<ProjectResponse> findAll(Pageable pageable);

    ProjectResponse update(Long id, UpdateProjectRequest request, CurrentUser actor);

    void delete(Long id, CurrentUser actor);
}
