package itmo.project.mapper;

import itmo.project.dto.CreateProjectRequest;
import itmo.project.dto.UpdateProjectRequest;
import itmo.project.entity.Project;
import itmo.project.entity.ProjectStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectMapperTest {

    private final ProjectMapper mapper = new ProjectMapper();

    @Test
    void shouldNormalizeProjectWhenMappingCreateRequest() {
        CreateProjectRequest request = new CreateProjectRequest(
                "  Platform  ",
                " platform_1 ",
                "  Main platform  ",
                ProjectStatus.PLANNED,
                1L
        );

        Project project = mapper.toEntity(request);

        assertThat(project.getName()).isEqualTo("Platform");
        assertThat(project.getCode()).isEqualTo("PLATFORM_1");
        assertThat(project.getDescription()).isEqualTo("Main platform");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.PLANNED);
    }

    @Test
    void shouldConvertBlankDescriptionToNullWhenUpdating() {
        Project project = new Project("Old", "OLD", "Description", ProjectStatus.PLANNED);
        UpdateProjectRequest request = new UpdateProjectRequest(
                " Platform ",
                "platform",
                "   ",
                ProjectStatus.ACTIVE
        );

        mapper.updateEntity(project, request);

        assertThat(project.getName()).isEqualTo("Platform");
        assertThat(project.getCode()).isEqualTo("PLATFORM");
        assertThat(project.getDescription()).isNull();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    }

    @Test
    void shouldKeepNullDescription() {
        Project project = mapper.toEntity(
                new CreateProjectRequest("Platform", "PLATFORM", null, ProjectStatus.ACTIVE, 1L)
        );

        assertThat(project.getDescription()).isNull();
    }
}
