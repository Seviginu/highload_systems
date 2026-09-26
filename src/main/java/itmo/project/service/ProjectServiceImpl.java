package itmo.project.service;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.project.dto.CreateProjectRequest;
import itmo.project.dto.ProjectResponse;
import itmo.project.dto.UpdateProjectRequest;
import itmo.project.entity.Project;
import itmo.project.mapper.ProjectMapper;
import itmo.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static itmo.common.persistence.ConstraintViolationDetector.isViolationOf;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final ProjectMemberService projectMemberService;

    @Override
    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        String normalizedCode = projectMapper.normalizeCode(request.code());
        ensureCodeAvailable(normalizedCode);

        try {
            Project project = projectRepository.saveAndFlush(projectMapper.toEntity(request));
            projectMemberService.assignTeamLead(project.getId(), request.teamLeadId());
            return projectMapper.toResponse(project);
        } catch (DataIntegrityViolationException exception) {
            if (isViolationOf(exception, "uq_projects_code")) {
                throw codeConflict(normalizedCode);
            }
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse findById(Long id) {
        return projectMapper.toResponse(findEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Project requireEntity(Long id) {
        return findEntity(id);
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
            if (isViolationOf(exception, "uq_projects_code")) {
                throw codeConflict(normalizedCode);
            }
            throw exception;
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
            if (isViolationOf(exception, "fk_tasks_project")) {
                throw new ConflictException("Project is referenced by tasks and cannot be deleted");
            }
            throw exception;
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
