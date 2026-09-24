package itmo.project.service;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.project.dto.CreateProjectRequest;
import itmo.project.dto.UpdateProjectRequest;
import itmo.project.entity.Project;
import itmo.project.entity.ProjectStatus;
import itmo.project.mapper.ProjectMapper;
import itmo.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberService projectMemberService;

    private ProjectServiceImpl projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectServiceImpl(projectRepository, new ProjectMapper(), projectMemberService);
    }

    @Test
    void shouldCreateProjectWithNormalizedValues() {
        CreateProjectRequest request = request(" Platform ", " platform ", ProjectStatus.PLANNED);
        when(projectRepository.existsByCode("PLATFORM")).thenReturn(false);
        when(projectRepository.saveAndFlush(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = projectService.create(request);

        assertThat(response.name()).isEqualTo("Platform");
        assertThat(response.code()).isEqualTo("PLATFORM");
        verify(projectRepository).saveAndFlush(any(Project.class));
        verify(projectMemberService).assignTeamLead(null, 1L);
    }

    @Test
    void shouldRejectDuplicateCode() {
        CreateProjectRequest request = request("Platform", "platform", ProjectStatus.PLANNED);
        when(projectRepository.existsByCode("PLATFORM")).thenReturn(true);

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PLATFORM");
        verify(projectRepository, never()).saveAndFlush(any(Project.class));
    }

    @Test
    void shouldTranslateDatabaseConflictDuringCreate() {
        CreateProjectRequest request = request("Platform", "PLATFORM", ProjectStatus.PLANNED);
        when(projectRepository.existsByCode("PLATFORM")).thenReturn(false);
        when(projectRepository.saveAndFlush(any(Project.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PLATFORM");
    }

    @Test
    void shouldReportMissingProject() {
        when(projectRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.findById(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void shouldReturnPageOfProjects() {
        Project project = project("Platform", "PLATFORM", ProjectStatus.ACTIVE);
        PageRequest pageable = PageRequest.of(0, 20);
        when(projectRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(project), pageable, 1));

        var result = projectService.findAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(response -> response.code())
                .containsExactly("PLATFORM");
    }

    @Test
    void shouldUpdateProject() {
        Project project = project("Platform", "PLATFORM", ProjectStatus.PLANNED);
        UpdateProjectRequest request = new UpdateProjectRequest(
                "Platform Core",
                "platform_core",
                "Core services",
                ProjectStatus.ACTIVE
        );
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.existsByCodeAndIdNot("PLATFORM_CORE", 1L)).thenReturn(false);
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        var response = projectService.update(1L, request);

        assertThat(response.name()).isEqualTo("Platform Core");
        assertThat(response.code()).isEqualTo("PLATFORM_CORE");
        assertThat(response.status()).isEqualTo(ProjectStatus.ACTIVE);
    }

    @Test
    void shouldRejectDuplicateCodeDuringUpdate() {
        Project project = project("Platform", "PLATFORM", ProjectStatus.PLANNED);
        UpdateProjectRequest request = new UpdateProjectRequest(
                "Platform",
                "BUSY",
                null,
                ProjectStatus.ACTIVE
        );
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.existsByCodeAndIdNot("BUSY", 1L)).thenReturn(true);

        assertThatThrownBy(() -> projectService.update(1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("BUSY");
        verify(projectRepository, never()).saveAndFlush(project);
    }

    @Test
    void shouldTranslateDatabaseConflictDuringUpdate() {
        Project project = project("Platform", "PLATFORM", ProjectStatus.PLANNED);
        UpdateProjectRequest request = new UpdateProjectRequest(
                "Platform",
                "PLATFORM_2",
                null,
                ProjectStatus.ACTIVE
        );
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.existsByCodeAndIdNot("PLATFORM_2", 1L)).thenReturn(false);
        when(projectRepository.saveAndFlush(project))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> projectService.update(1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PLATFORM_2");
    }

    @Test
    void shouldDeleteExistingProject() {
        Project project = project("Platform", "PLATFORM", ProjectStatus.PLANNED);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        projectService.delete(1L);

        verify(projectRepository).delete(project);
        verify(projectRepository).flush();
    }

    @Test
    void shouldRejectDeletionOfReferencedProject() {
        Project project = project("Platform", "PLATFORM", ProjectStatus.PLANNED);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        doThrow(new DataIntegrityViolationException("foreign key"))
                .when(projectRepository).flush();

        assertThatThrownBy(() -> projectService.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("referenced");
    }

    private CreateProjectRequest request(String name, String code, ProjectStatus status) {
        return new CreateProjectRequest(name, code, "Description", status, 1L);
    }

    private Project project(String name, String code, ProjectStatus status) {
        return new Project(name, code, "Description", status);
    }
}
