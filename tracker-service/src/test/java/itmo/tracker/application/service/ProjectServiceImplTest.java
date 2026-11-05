package itmo.tracker.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import itmo.support.TestTransactions;
import itmo.tracker.application.exception.ConflictException;
import itmo.tracker.application.exception.ResourceNotFoundException;
import itmo.tracker.application.mapper.ProjectMapper;
import itmo.tracker.application.model.CreateProjectCommand;
import itmo.tracker.application.model.PageQuery;
import itmo.tracker.application.model.ProjectView;
import itmo.tracker.application.model.ResultPage;
import itmo.tracker.application.model.UpdateProjectCommand;
import itmo.tracker.application.port.in.ProjectMemberService;
import itmo.tracker.application.port.out.ProjectStore;
import itmo.tracker.application.port.out.UserDirectoryPort;
import itmo.tracker.domain.Project;
import itmo.tracker.domain.ProjectStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock private ProjectStore projectRepository;

    @Mock private ProjectMemberService projectMemberService;

    @Mock private UserDirectoryPort userDirectory;

    private ProjectServiceImpl projectService;

    @BeforeEach
    void setUp() {
        projectService =
                new ProjectServiceImpl(
                        itmo.support.TestActors.access(),
                        projectRepository,
                        new ProjectMapper(),
                        projectMemberService,
                        userDirectory,
                        new TestTransactions());
    }

    @Test
    void shouldCreateProjectWithNormalizedValues() {
        CreateProjectCommand request = request(" Platform ", " platform ");
        when(projectRepository.existsByCode("PLATFORM")).thenReturn(false);
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = projectService.create(request, itmo.support.TestActors.admin());

        assertThat(response.name()).isEqualTo("Platform");
        assertThat(response.code()).isEqualTo("PLATFORM");
        verify(projectRepository).save(any(Project.class));
        verify(projectMemberService).assignValidatedTeamLead(null, 1L);
    }

    @Test
    void shouldRejectDuplicateCode() {
        CreateProjectCommand request = request("Platform", "platform");
        when(projectRepository.existsByCode("PLATFORM")).thenReturn(true);

        assertThatThrownBy(() -> projectService.create(request, itmo.support.TestActors.admin()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PLATFORM");
        verify(projectRepository, never()).save(any(Project.class));
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
        Project project = project(ProjectStatus.ACTIVE);
        PageQuery pageable = PageQuery.of(0, 20);
        when(projectRepository.findAll(pageable)).thenReturn(new ResultPage<>(List.of(project), 1));

        var result = projectService.findAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(ProjectView::code).containsExactly("PLATFORM");
    }

    @Test
    void shouldUpdateProject() {
        Project project = project(ProjectStatus.PLANNED);
        UpdateProjectCommand request =
                new UpdateProjectCommand(
                        "Platform Core", "platform_core", "Core services", ProjectStatus.ACTIVE);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.existsByCodeAndIdNot("PLATFORM_CORE", 1L)).thenReturn(false);
        when(projectRepository.save(project)).thenReturn(project);

        var response = projectService.update(1L, request, itmo.support.TestActors.admin());

        assertThat(response.name()).isEqualTo("Platform Core");
        assertThat(response.code()).isEqualTo("PLATFORM_CORE");
        assertThat(response.status()).isEqualTo(ProjectStatus.ACTIVE);
    }

    @Test
    void shouldRejectDuplicateCodeDuringUpdate() {
        Project project = project(ProjectStatus.PLANNED);
        UpdateProjectCommand request =
                new UpdateProjectCommand("Platform", "BUSY", null, ProjectStatus.ACTIVE);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.existsByCodeAndIdNot("BUSY", 1L)).thenReturn(true);

        assertThatThrownBy(
                        () -> projectService.update(1L, request, itmo.support.TestActors.admin()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("BUSY");
        verify(projectRepository, never()).save(project);
    }

    @Test
    void shouldDeleteExistingProject() {
        Project project = project(ProjectStatus.PLANNED);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        projectService.delete(1L, itmo.support.TestActors.admin());

        verify(projectRepository).delete(project);
    }

    private CreateProjectCommand request(String name, String code) {
        return new CreateProjectCommand(name, code, "Description", ProjectStatus.PLANNED, 1L);
    }

    private Project project(ProjectStatus status) {
        return new Project("Platform", "PLATFORM", "Description", status);
    }
}
