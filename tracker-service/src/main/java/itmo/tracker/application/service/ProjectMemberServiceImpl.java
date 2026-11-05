package itmo.tracker.application.service;

import itmo.tracker.application.exception.ConflictException;
import itmo.tracker.application.exception.ResourceNotFoundException;
import itmo.tracker.application.mapper.ProjectMemberMapper;
import itmo.tracker.application.model.Actor;
import itmo.tracker.application.model.AddProjectMemberCommand;
import itmo.tracker.application.model.PageQuery;
import itmo.tracker.application.model.ProjectMemberView;
import itmo.tracker.application.model.ResultPage;
import itmo.tracker.application.port.in.*;
import itmo.tracker.application.port.out.ProjectMemberStore;
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
            ProjectMemberMapper memberMapper) {
        this.access = access;
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
        this.userDirectory = userDirectory;
        this.transactions = transactions;
        this.memberMapper = memberMapper;
    }

    private final TrackerAccess access;

    private final ProjectMemberStore memberRepository;
    private final ProjectStore projectRepository;
    private final UserDirectoryPort userDirectory;
    private final UnitOfWork transactions;
    private final ProjectMemberMapper memberMapper;

    @Override
    public ProjectMemberView add(Long projectId, AddProjectMemberCommand request, Actor actor) {
        access.requireManager(actor);
        userDirectory.requireUsers(request.userId());
        return transactions.write(
                () -> {
                    access.requireProjectManager(projectId, actor);
                    return addMember(projectId, request.userId());
                });
    }

    @Override
    public ProjectMemberView assignValidatedTeamLead(Long projectId, Long userId) {
        return transactions.write(
                () -> {
                    return addMember(projectId, userId);
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

    private ProjectMemberView addMember(Long projectId, Long userId) {
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
            return memberMapper.toResponse(memberRepository.save(member));
        }

        return memberMapper.toResponse(memberRepository.save(new ProjectMember(project, userId)));
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
