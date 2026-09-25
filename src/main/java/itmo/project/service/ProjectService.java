package itmo.project.service;

import itmo.project.dto.CreateProjectRequest;
import itmo.project.dto.ProjectResponse;
import itmo.project.dto.UpdateProjectRequest;
import itmo.project.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProjectService {

    ProjectResponse create(CreateProjectRequest request);

    ProjectResponse findById(Long id);

    Project requireEntity(Long id);

    Page<ProjectResponse> findAll(Pageable pageable);

    ProjectResponse update(Long id, UpdateProjectRequest request);

    void delete(Long id);
}
