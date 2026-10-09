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
import itmo.integration.user.UserDirectory;
import org.springframework.transaction.support.TransactionOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static itmo.common.persistence.ConstraintViolationDetector.isViolationOf;

@Service
@RequiredArgsConstructor
public class ProjectMemberServiceImpl implements ProjectMemberService {

    private final ProjectMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final UserDirectory userDirectory;
    private final TransactionOperations transactions;
    private final ProjectMemberMapper memberMapper;

    @Override
    public ProjectMemberResponse add(Long projectId, AddProjectMemberRequest request) {
        userDirectory.requireUsers(request.userId());
        return transactions.execute(status -> addMember(projectId, request.userId()));
    }

    @Override
    @Transactional
    public ProjectMemberResponse assignValidatedTeamLead(Long projectId, Long userId) {
        return addMember(projectId, userId);
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

    private ProjectMemberResponse addMember(Long projectId, Long userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        var existingMember = memberRepository.findByProjectIdAndUserId(projectId, userId);
        if (existingMember.isPresent()) {
            ProjectMember member = existingMember.get();
            if (member.isActive()) {
                throw memberConflict(projectId, userId);
            }
            member.activate();
            return memberMapper.toResponse(memberRepository.saveAndFlush(member));
        }

        try {
            return memberMapper.toResponse(memberRepository.saveAndFlush(new ProjectMember(project, userId)));
        } catch (DataIntegrityViolationException exception) {
            if (isViolationOf(exception, "uq_project_members_project_user")) {
                throw memberConflict(projectId, userId);
            }
            throw exception;
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
