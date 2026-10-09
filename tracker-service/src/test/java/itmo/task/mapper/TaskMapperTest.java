package itmo.task.mapper;

import itmo.label.entity.Label;
import itmo.project.entity.Project;
import itmo.project.entity.ProjectStatus;
import itmo.task.dto.CreateTaskRequest;
import itmo.task.dto.UpdateTaskRequest;
import itmo.task.entity.Task;
import itmo.task.entity.TaskPriority;
import itmo.task.entity.TaskStatus;
import itmo.user.entity.User;
import itmo.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TaskMapperTest {

    private final TaskMapper mapper = new TaskMapper();
    private Project project;
    private User author;
    private User assignee;
    private Label backend;

    @BeforeEach
    void setUp() {
        project = new Project("Platform", "PLATFORM", null, ProjectStatus.ACTIVE);
        author = new User("Author", "author@example.com", UserRole.TEAM_LEAD);
        assignee = new User("Developer", "dev@example.com", UserRole.DEVELOPER);
        backend = new Label("Backend", "#112233");
        ReflectionTestUtils.setField(project, "id", 1L);
        ReflectionTestUtils.setField(author, "id", 2L);
        ReflectionTestUtils.setField(assignee, "id", 3L);
        ReflectionTestUtils.setField(backend, "id", 4L);
    }

    @Test
    void shouldNormalizeTaskAndMapRelations() {
        Task task = mapper.toEntity(
                new CreateTaskRequest(
                        " platform-1 ",
                        " First task ",
                        " Description ",
                        TaskStatus.TODO,
                        TaskPriority.HIGH,
                        2L,
                        3L,
                        1L,
                        Set.of(4L)
                ),
                author,
                assignee,
                project,
                Set.of(backend)
        );
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
        Task task = mapper.toEntity(
                new CreateTaskRequest(
                        "PLATFORM-1", "Old", null, TaskStatus.TODO, TaskPriority.LOW,
                        2L, 3L, 1L, Set.of(4L)
                ),
                author,
                assignee,
                project,
                Set.of(backend)
        );
        Project anotherProject = new Project("Core", "CORE", null, ProjectStatus.ACTIVE);
        Label api = new Label("API", null);
        ReflectionTestUtils.setField(anotherProject, "id", 6L);
        ReflectionTestUtils.setField(api, "id", 7L);

        mapper.updateEntity(
                task,
                new UpdateTaskRequest(
                        "core-2", " New ", " ", TaskStatus.IN_PROGRESS, TaskPriority.CRITICAL,
                        2L, null, 6L, Set.of(7L), 0L
                ),
                author,
                null,
                anotherProject,
                Set.of(api)
        );

        assertThat(task.getTaskKey()).isEqualTo("CORE-2");
        assertThat(task.getDescription()).isNull();
        assertThat(task.getAssignee()).isNull();
        assertThat(task.getProject()).isSameAs(anotherProject);
        assertThat(task.getLabels()).containsExactly(api);
        assertThat(project.getTasks()).doesNotContain(task);
        assertThat(backend.getTasks()).doesNotContain(task);
    }
}
