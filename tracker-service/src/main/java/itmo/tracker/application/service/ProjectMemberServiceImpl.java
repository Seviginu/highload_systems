package itmo.tracker.application.service;

import itmo.tracker.application.exception.ConflictException;
import itmo.tracker.application.exception.ResourceNotFoundException;
import itmo.tracker.application.mapper.ProjectMemberMapper;
import itmo.tracker.application.model.Actor;
import itmo.tracker.application.model.AddProjectMemberCommand;
import itmo.tracker.application.model.PageQuery;
import itmo.tracker.application.model.ProjectMemberView;
import itmo.tracker.application.model.ProjectMembership;
import itmo.tracker.application.model.ResultPage;
import itmo.tracker.application.port.in.*;
import itmo.tracker.application.port.out.ProjectMemberStore;
import itmo.tracker.application.port.out.ProjectMemberships;
import itmo.tracker.application.port.out.ProjectStore;
import itmo.tracker.application.port.out.UnitOfWork;
import itmo.tracker.application.port.out.UserDirectoryPort;
import itmo.tracker.domain.Project;
import itmo.tracker.domain.ProjectMember;

public class ProjectMemberServiceImpl implements ProjectMemberService {
    public ProjectMemberServiceImpl(
            TrackerAccess access,
            ProjectMemberStore memberRepository,
            ProjectStore projectRepository,
            UserDirectoryPort userDirectory,
            UnitOfWork transactions,
            ProjectMemberMapper memberMapper,
            ProjectMemberships events) {
        this.access = access;
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
        this.userDirectory = userDirectory;
        this.transactions = transactions;
        this.memberMapper = memberMapper;
        this.events = events;
    }

    private final TrackerAccess access;

    private final ProjectMemberStore memberRepository;
    private final ProjectStore projectRepository;
    private final UserDirectoryPort userDirectory;
    private final UnitOfWork transactions;
    private final ProjectMemberMapper memberMapper;
    private final ProjectMemberships events;

    @Override
    public ProjectMemberView add(Long projectId, AddProjectMemberCommand request, Actor actor) {
        access.requireManager(actor);
        userDirectory.requireUsers(request.userId());
        return transactions.write(
                () -> {
                    access.requireProjectManager(projectId, actor);
                    return addMember(projectId, request.userId(), actor);
                });
    }

    @Override
    public ProjectMemberView assignValidatedTeamLead(Long projectId, Long userId, Actor actor) {
        return transactions.write(
                () -> {
                    return addMember(projectId, userId, actor);
                });
    }

    @Override
    public ResultPage<ProjectMemberView> findAll(Long projectId, PageQuery pageable) {
        return transactions.read(
                () -> {
                    ensureProjectExists(projectId);
                    return memberRepository
                            .findAllByProjectId(projectId, pageable)
                            .map(memberMapper::toResponse);
                });
    }

    @Override
    public boolean isActiveMember(Long projectId, Long userId) {
        return transactions.read(
                () -> {
                    return memberRepository.existsByProjectIdAndUserIdAndActiveTrue(
                            projectId, userId);
                });
    }

    @Override
    public void deactivate(Long projectId, Long memberId, Actor actor) {
        transactions.write(
                () -> {
                    access.requireProjectManager(projectId, actor);
                    ensureProjectExists(projectId);
                    ProjectMember member =
                            memberRepository
                                    .findByIdAndProjectId(memberId, projectId)
                                    .orElseThrow(
                                            () ->
                                                    new ResourceNotFoundException(
                                                            "Project member", memberId));
                    member.deactivate();
                    memberRepository.save(member);
                    return null;
                });
    }

    private ProjectMemberView addMember(Long projectId, Long userId, Actor actor) {
        Project project =
                projectRepository
                        .findById(projectId)
                        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        var existingMember = memberRepository.findByProjectIdAndUserId(projectId, userId);
        if (existingMember.isPresent()) {
            ProjectMember member = existingMember.get();
            if (member.isActive()) {
                throw memberConflict(projectId, userId);
            }
            member.activate();
            return saveMember(member, project, actor);
        }

        return saveMember(new ProjectMember(project, userId), project, actor);
    }

    private ProjectMemberView saveMember(ProjectMember member, Project project, Actor actor) {
        ProjectMember saved = memberRepository.save(member);
        events.append(
                new ProjectMembership(
                        project.getId(),
                        saved.getId(),
                        project.getName(),
                        actor.id(),
                        saved.getUserId()));
        return memberMapper.toResponse(saved);
    }

    private void ensureProjectExists(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
    }

    private ConflictException memberConflict(Long projectId, Long userId) {
        return new ConflictException(
                "User with id '%d' is already an active member of project '%d'"
                        .formatted(userId, projectId));
    }
}
