package itmo.project.service;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.project.dto.AddProjectMemberRequest;
import itmo.project.dto.ProjectMemberResponse;
import itmo.project.entity.Project;
import itmo.project.entity.ProjectMember;
import itmo.project.entity.ProjectStatus;
import itmo.project.mapper.ProjectMemberMapper;
import itmo.project.repository.ProjectMemberRepository;
import itmo.project.repository.ProjectRepository;
import itmo.user.dto.UserResponse;
import itmo.user.entity.User;
import itmo.user.entity.UserRole;
import itmo.user.service.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMemberServiceImplTest {

    @Mock
    private ProjectMemberRepository memberRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserService userService;

    @Mock
    private EntityManager entityManager;

    private ProjectMemberServiceImpl memberService;
    private Project project;
    private User user;

    @BeforeEach
    void setUp() {
        memberService = new ProjectMemberServiceImpl(
                memberRepository,
                projectRepository,
                userService,
                new ProjectMemberMapper(),
                entityManager
        );
        project = new Project("Platform", "PLATFORM", null, ProjectStatus.ACTIVE);
        user = new User("Alice", "alice@example.com", UserRole.DEVELOPER);
        ReflectionTestUtils.setField(project, "id", 1L);
        ReflectionTestUtils.setField(user, "id", 2L);
    }

    @Test
    void shouldAddMember() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(userService.findById(2L)).thenReturn(userResponse());
        when(memberRepository.findByProjectIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        when(entityManager.getReference(User.class, 2L)).thenReturn(user);
        when(memberRepository.saveAndFlush(any(ProjectMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = memberService.add(1L, new AddProjectMemberRequest(2L));

        assertThat(response.projectId()).isEqualTo(1L);
        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.active()).isTrue();
    }

    @Test
    void shouldRejectActiveDuplicate() {
        ProjectMember existing = new ProjectMember(project, user);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(userService.findById(2L)).thenReturn(userResponse());
        when(memberRepository.findByProjectIdAndUserId(1L, 2L)).thenReturn(Optional.of(existing));
        AddProjectMemberRequest request = new AddProjectMemberRequest(2L);

        assertThatThrownBy(() -> memberService.add(1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already an active member");
        verify(memberRepository, never()).saveAndFlush(existing);
    }

    @Test
    void shouldReactivateInactiveMember() {
        ProjectMember existing = new ProjectMember(project, user);
        existing.deactivate();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(userService.findById(2L)).thenReturn(userResponse());
        when(memberRepository.findByProjectIdAndUserId(1L, 2L)).thenReturn(Optional.of(existing));
        when(memberRepository.saveAndFlush(existing)).thenReturn(existing);

        var response = memberService.add(1L, new AddProjectMemberRequest(2L));

        assertThat(response.active()).isTrue();
    }

    @Test
    void shouldRequireTeamLeadRoleForProjectCreation() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(userService.findById(2L)).thenReturn(userResponse());

        assertThatThrownBy(() -> memberService.assignTeamLead(1L, 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not a team lead");
        verify(memberRepository, never()).saveAndFlush(any(ProjectMember.class));
    }

    @Test
    void shouldReturnMemberPage() {
        ProjectMember member = new ProjectMember(project, user);
        PageRequest pageable = PageRequest.of(0, 20);
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(memberRepository.findAllByProjectId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(member), pageable, 1));

        var result = memberService.findAll(1L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(ProjectMemberResponse::userId).containsExactly(2L);
    }

    @Test
    void shouldCheckActiveMembership() {
        when(memberRepository.existsByProjectIdAndUserIdAndActiveTrue(1L, 2L)).thenReturn(true);

        assertThat(memberService.isActiveMember(1L, 2L)).isTrue();
    }

    @Test
    void shouldDeactivateMember() {
        ProjectMember member = new ProjectMember(project, user);
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(memberRepository.findByIdAndProjectId(3L, 1L)).thenReturn(Optional.of(member));
        when(memberRepository.saveAndFlush(member)).thenReturn(member);

        memberService.deactivate(1L, 3L);

        assertThat(member.isActive()).isFalse();
        verify(memberRepository).saveAndFlush(member);
    }

    @Test
    void shouldReportMissingProject() {
        when(projectRepository.existsById(42L)).thenReturn(false);
        PageRequest pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> memberService.findAll(42L, pageable))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("42");
    }

    private UserResponse userResponse() {
        return new UserResponse(2L, "Alice", "alice@example.com", UserRole.DEVELOPER, Instant.now(), Instant.now());
    }
}
