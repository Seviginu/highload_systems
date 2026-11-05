package itmo.tracker.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import itmo.support.TestUser;
import itmo.tracker.application.model.CreateTaskCommand;
import itmo.tracker.application.model.UpdateTaskCommand;
import itmo.tracker.domain.Label;
import itmo.tracker.domain.Project;
import itmo.tracker.domain.ProjectStatus;
import itmo.tracker.domain.Task;
import itmo.tracker.domain.TaskPriority;
import itmo.tracker.domain.TaskStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

class TaskMapperTest {

    private final TaskMapper mapper = new TaskMapper();
    private Project project;
    private TestUser author;
    private TestUser assignee;
    private Label backend;

    @BeforeEach
    void setUp() {
        project = new Project("Platform", "PLATFORM", null, ProjectStatus.ACTIVE);
        author = new TestUser("TEAM_LEAD");
        assignee = new TestUser("DEVELOPER");
        backend = new Label("Backend", "#112233");
        ReflectionTestUtils.setField(project, "id", 1L);
        ReflectionTestUtils.setField(author, "id", 2L);
        ReflectionTestUtils.setField(assignee, "id", 3L);
        ReflectionTestUtils.setField(backend, "id", 4L);
    }

    @Test
    void shouldNormalizeTaskAndMapRelations() {
        Task task =
                mapper.toDomain(
                        new CreateTaskCommand(
                                " platform-1 ",
                                " First task ",
                                " Description ",
                                TaskStatus.TODO,
                                TaskPriority.HIGH,
                                3L,
                                1L,
                                Set.of(4L)),
                        author.getId(),
                        assignee.getId(),
                        project,
                        Set.of(backend));
        ReflectionTestUtils.setField(task, "id", 5L);
        ReflectionTestUtils.setField(task, "version", 0L);

        var response = mapper.toResponse(task);

        assertThat(response.taskKey()).isEqualTo("PLATFORM-1");
        assertThat(response.title()).isEqualTo("First task");
        assertThat(response.description()).isEqualTo("Description");
        assertThat(response.projectId()).isEqualTo(1L);
        assertThat(response.authorId()).isEqualTo(2L);
        assertThat(response.assigneeId()).isEqualTo(3L);
        assertThat(response.labelIds()).containsExactly(4L);
        assertThat(project.getTasks()).contains(task);
        assertThat(backend.getTasks()).contains(task);
    }

    @Test
    void shouldReplaceProjectLabelsAndOptionalAssignee() {
        Task task =
                mapper.toDomain(
                        new CreateTaskCommand(
                                "PLATFORM-1",
                                "Old",
                                null,
                                TaskStatus.TODO,
                                TaskPriority.LOW,
                                3L,
                                1L,
                                Set.of(4L)),
                        author.getId(),
                        assignee.getId(),
                        project,
                        Set.of(backend));
        Project anotherProject = new Project("Core", "CORE", null, ProjectStatus.ACTIVE);
        Label api = new Label("API", null);
        ReflectionTestUtils.setField(anotherProject, "id", 6L);
        ReflectionTestUtils.setField(api, "id", 7L);

        mapper.updateDomain(
                task,
                new UpdateTaskCommand(
                        "core-2",
                        " New ",
                        " ",
                        TaskStatus.IN_PROGRESS,
                        TaskPriority.CRITICAL,
                        null,
                        6L,
                        Set.of(7L),
                        0L),
                author.getId(),
                null,
                anotherProject,
                Set.of(api));

        assertThat(task.getTaskKey()).isEqualTo("CORE-2");
        assertThat(task.getDescription()).isNull();
        assertThat(task.getAssigneeId()).isNull();
        assertThat(task.getProject()).isSameAs(anotherProject);
        assertThat(task.getLabels()).containsExactly(api);
        assertThat(project.getTasks()).doesNotContain(task);
        assertThat(backend.getTasks()).doesNotContain(task);
    }
}
