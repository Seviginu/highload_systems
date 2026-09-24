package itmo.project.service;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.project.dto.AddProjectMemberRequest;
import itmo.project.dto.ProjectMemberResponse;
import itmo.project.entity.Project;
import itmo.project.entity.ProjectMember;
import itmo.project.mapper.ProjectMemberMapper;
import itmo.project.repository.ProjectMemberRepository;
import itmo.project.repository.ProjectRepository;
import itmo.user.entity.User;
import itmo.user.entity.UserRole;
import itmo.user.service.UserService;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectMemberServiceImpl implements ProjectMemberService {

    private final ProjectMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final UserService userService;
    private final ProjectMemberMapper memberMapper;
    private final EntityManager entityManager;

    public ProjectMemberServiceImpl(
            ProjectMemberRepository memberRepository,
            ProjectRepository projectRepository,
            UserService userService,
            ProjectMemberMapper memberMapper,
            EntityManager entityManager
    ) {
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
        this.userService = userService;
        this.memberMapper = memberMapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public ProjectMemberResponse add(Long projectId, AddProjectMemberRequest request) {
        return addMember(projectId, request.userId(), false);
    }

    @Override
    @Transactional
    public ProjectMemberResponse assignTeamLead(Long projectId, Long userId) {
        return addMember(projectId, userId, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectMemberResponse> findAll(Long projectId, Pageable pageable) {
        ensureProjectExists(projectId);
        return memberRepository.findAllByProjectId(projectId, pageable).map(memberMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveMember(Long projectId, Long userId) {
        return memberRepository.existsByProjectIdAndUserIdAndActiveTrue(projectId, userId);
    }

    @Override
    @Transactional
    public void deactivate(Long projectId, Long memberId) {
        ensureProjectExists(projectId);
        ProjectMember member = memberRepository.findByIdAndProjectId(memberId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project member", memberId));
        member.deactivate();
        memberRepository.saveAndFlush(member);
    }

    private ProjectMemberResponse addMember(Long projectId, Long userId, boolean teamLeadRequired) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        var userResponse = userService.findById(userId);

        if (teamLeadRequired && userResponse.role() != UserRole.TEAM_LEAD) {
            throw new ConflictException("User with id '%d' is not a team lead".formatted(userId));
        }

        var existingMember = memberRepository.findByProjectIdAndUserId(projectId, userId);
        if (existingMember.isPresent()) {
            ProjectMember member = existingMember.get();
            if (member.isActive()) {
                throw memberConflict(projectId, userId);
            }
            member.activate();
            return memberMapper.toResponse(memberRepository.saveAndFlush(member));
        }

        User user = entityManager.getReference(User.class, userId);
        try {
            return memberMapper.toResponse(memberRepository.saveAndFlush(new ProjectMember(project, user)));
        } catch (DataIntegrityViolationException exception) {
            throw memberConflict(projectId, userId);
        }
    }

    private void ensureProjectExists(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
    }

    private ConflictException memberConflict(Long projectId, Long userId) {
        return new ConflictException(
                "User with id '%d' is already an active member of project '%d'".formatted(userId, projectId)
        );
    }
}
