package itmo.tracker.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import itmo.tracker.application.model.CreateProjectCommand;
import itmo.tracker.application.model.UpdateProjectCommand;
import itmo.tracker.domain.Project;
import itmo.tracker.domain.ProjectStatus;

import org.junit.jupiter.api.Test;

class ProjectMapperTest {

    private final ProjectMapper mapper = new ProjectMapper();

    @Test
    void shouldNormalizeProjectWhenMappingCreateRequest() {
        CreateProjectCommand request =
                new CreateProjectCommand(
                        "  Platform  ",
                        " platform_1 ",
                        "  Main platform  ",
                        ProjectStatus.PLANNED,
                        1L);

        Project project = mapper.toDomain(request);

        assertThat(project.getName()).isEqualTo("Platform");
        assertThat(project.getCode()).isEqualTo("PLATFORM_1");
        assertThat(project.getDescription()).isEqualTo("Main platform");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.PLANNED);
    }

    @Test
    void shouldConvertBlankDescriptionToNullWhenUpdating() {
        Project project = new Project("Old", "OLD", "Description", ProjectStatus.PLANNED);
        UpdateProjectCommand request =
                new UpdateProjectCommand(" Platform ", "platform", "   ", ProjectStatus.ACTIVE);

        mapper.updateDomain(project, request);

        assertThat(project.getName()).isEqualTo("Platform");
        assertThat(project.getCode()).isEqualTo("PLATFORM");
        assertThat(project.getDescription()).isNull();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    }

    @Test
    void shouldKeepNullDescription() {
        Project project =
                mapper.toDomain(
                        new CreateProjectCommand(
                                "Platform", "PLATFORM", null, ProjectStatus.ACTIVE, 1L));

        assertThat(project.getDescription()).isNull();
    }
}
