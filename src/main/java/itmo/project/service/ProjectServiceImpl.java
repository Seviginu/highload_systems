package itmo.project.service;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.project.dto.CreateProjectRequest;
import itmo.project.dto.ProjectResponse;
import itmo.project.dto.UpdateProjectRequest;
import itmo.project.entity.Project;
import itmo.project.mapper.ProjectMapper;
import itmo.project.repository.ProjectRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;

    public ProjectServiceImpl(ProjectRepository projectRepository, ProjectMapper projectMapper) {
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
    }

    @Override
    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        String normalizedCode = projectMapper.normalizeCode(request.code());
        ensureCodeAvailable(normalizedCode);

        try {
            return projectMapper.toResponse(projectRepository.saveAndFlush(projectMapper.toEntity(request)));
        } catch (DataIntegrityViolationException exception) {
            throw codeConflict(normalizedCode);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse findById(Long id) {
        return projectMapper.toResponse(findEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectResponse> findAll(Pageable pageable) {
        return projectRepository.findAll(pageable).map(projectMapper::toResponse);
    }

    @Override
    @Transactional
    public ProjectResponse update(Long id, UpdateProjectRequest request) {
        Project project = findEntity(id);
        String normalizedCode = projectMapper.normalizeCode(request.code());

        if (projectRepository.existsByCodeAndIdNot(normalizedCode, id)) {
            throw codeConflict(normalizedCode);
        }

        projectMapper.updateEntity(project, request);
        try {
            return projectMapper.toResponse(projectRepository.saveAndFlush(project));
        } catch (DataIntegrityViolationException exception) {
            throw codeConflict(normalizedCode);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Project project = findEntity(id);
        try {
            projectRepository.delete(project);
            projectRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Project is referenced by other records and cannot be deleted");
        }
    }

    private Project findEntity(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    private void ensureCodeAvailable(String code) {
        if (projectRepository.existsByCode(code)) {
            throw codeConflict(code);
        }
    }

    private ConflictException codeConflict(String code) {
        return new ConflictException("Project with code '%s' already exists".formatted(code));
    }
}
