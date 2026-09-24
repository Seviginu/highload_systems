package itmo.project.service;

import itmo.project.dto.CreateProjectRequest;
import itmo.project.dto.ProjectResponse;
import itmo.project.dto.UpdateProjectRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProjectService {

    ProjectResponse create(CreateProjectRequest request);

    ProjectResponse findById(Long id);

    Page<ProjectResponse> findAll(Pageable pageable);

    ProjectResponse update(Long id, UpdateProjectRequest request);

    void delete(Long id);
}
