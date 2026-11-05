package itmo.tracker.application.service;

import itmo.tracker.application.exception.ConflictException;
import itmo.tracker.application.exception.ForbiddenException;
import itmo.tracker.application.exception.ResourceNotFoundException;
import itmo.tracker.application.mapper.ProjectMapper;
import itmo.tracker.application.model.Actor;
import itmo.tracker.application.model.CreateProjectCommand;
import itmo.tracker.application.model.PageQuery;
import itmo.tracker.application.model.ProjectView;
import itmo.tracker.application.model.ResultPage;
import itmo.tracker.application.model.UpdateProjectCommand;
import itmo.tracker.application.port.in.*;
import itmo.tracker.application.port.out.ProjectStore;
import itmo.tracker.application.port.out.UnitOfWork;
import itmo.tracker.application.port.out.UserDirectoryPort;
import itmo.tracker.domain.Project;

import java.util.Objects;

public class ProjectServiceImpl implements ProjectService {
    public ProjectServiceImpl(
            TrackerAccess access,
            ProjectStore projectRepository,
            ProjectMapper projectMapper,
            ProjectMemberService projectMemberService,
            UserDirectoryPort userDirectory,
            UnitOfWork transactions) {
        this.access = access;
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.projectMemberService = projectMemberService;
        this.userDirectory = userDirectory;
        this.transactions = transactions;
    }

    private final TrackerAccess access;

    private final ProjectStore projectRepository;
    private final ProjectMapper projectMapper;
    private final ProjectMemberService projectMemberService;
    private final UserDirectoryPort userDirectory;
    private final UnitOfWork transactions;

    @Override
    public ProjectView create(CreateProjectCommand request, Actor actor) {
        access.requireManager(actor);
        if (!actor.isAdmin() && !Objects.equals(request.teamLeadId(), actor.id())) {
            throw new ForbiddenException("A team lead can only assign themselves");
        }
        userDirectory.requireTeamLead(request.teamLeadId());
        return transactions.write(
                () -> {
                    String normalizedCode = projectMapper.normalizeCode(request.code());
                    ensureCodeAvailable(normalizedCode);

                    Project project = projectRepository.save(projectMapper.toDomain(request));
                    projectMemberService.assignValidatedTeamLead(
                            project.getId(), request.teamLeadId());
                    return projectMapper.toResponse(project);
                });
    }

    @Override
    public ProjectView findById(Long id) {
        return transactions.read(
                () -> {
                    return projectMapper.toResponse(findEntity(id));
                });
    }

    @Override
    public Project requireEntity(Long id) {
        return transactions.read(
                () -> {
                    return findEntity(id);
                });
    }

    @Override
    public ResultPage<ProjectView> findAll(PageQuery pageable) {
        return transactions.read(
                () -> {
                    return projectRepository.findAll(pageable).map(projectMapper::toResponse);
                });
    }

    @Override
    public ProjectView update(Long id, UpdateProjectCommand request, Actor actor) {
        return transactions.write(
                () -> {
                    access.requireProjectManager(id, actor);
                    Project project = findEntity(id);
                    String normalizedCode = projectMapper.normalizeCode(request.code());

                    if (projectRepository.existsByCodeAndIdNot(normalizedCode, id)) {
                        throw codeConflict(normalizedCode);
                    }

                    projectMapper.updateDomain(project, request);

                    return projectMapper.toResponse(projectRepository.save(project));
                });
    }

    @Override
    public void delete(Long id, Actor actor) {
        transactions.write(
                () -> {
                    access.requireProjectManager(id, actor);
                    Project project = findEntity(id);

                    projectRepository.delete(project);

                    return null;
                });
    }

    private Project findEntity(Long id) {
        return projectRepository
                .findById(id)
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
