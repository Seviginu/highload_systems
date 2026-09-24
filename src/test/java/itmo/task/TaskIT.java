package itmo.task;

import itmo.label.entity.Label;
import itmo.label.repository.LabelRepository;
import itmo.project.entity.Project;
import itmo.project.entity.ProjectStatus;
import itmo.project.repository.ProjectMemberRepository;
import itmo.project.repository.ProjectRepository;
import itmo.task.dto.CreateTaskRequest;
import itmo.task.dto.TaskResponse;
import itmo.task.dto.UpdateTaskRequest;
import itmo.task.entity.Task;
import itmo.task.entity.TaskPriority;
import itmo.task.entity.TaskStatus;
import itmo.task.repository.TaskRepository;
import itmo.user.entity.User;
import itmo.user.entity.UserRole;
import itmo.user.repository.UserRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.file.name=target/task-it.log"
)
class TaskIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Validator validator;

    @BeforeEach
    void cleanDatabase() {
        taskRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        labelRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldApplyTaskLiquibaseChangesets() {
        Integer changesetCount = jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM databasechangelog
                        WHERE id IN ('004-create-tasks', '006-create-task-labels')
                          AND author = 'vs-lab1'
                        """,
                Integer.class
        );

        assertThat(changesetCount).isEqualTo(2);
    }

    @Test
    void shouldPerformTaskCrudWithRelationsAndOptimisticVersion() {
        TestData data = createTestData();
        var createResponse = restTemplate.postForEntity(
                "/api/tasks",
                createRequest(data, "platform-1"),
                TaskResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getHeaders().getLocation()).isNotNull();
        TaskResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.taskKey()).isEqualTo("PLATFORM-1");
        assertThat(created.authorId()).isEqualTo(data.author().getId());
        assertThat(created.assigneeId()).isEqualTo(data.assignee().getId());
        assertThat(created.projectId()).isEqualTo(data.project().getId());
        assertThat(created.labelIds()).containsExactly(data.label().getId());
        assertThat(created.version()).isZero();

        Task persisted = taskRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.getProject().getId()).isEqualTo(data.project().getId());
        assertThat(persisted.getLabels()).extracting(Label::getId).containsExactly(data.label().getId());

        var listResponse = restTemplate.getForEntity("/api/tasks?page=0&size=20", TaskResponse[].class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");

        UpdateTaskRequest updateRequest = new UpdateTaskRequest(
                "platform-1",
                "Updated task",
                null,
                TaskStatus.IN_PROGRESS,
                TaskPriority.CRITICAL,
                data.author().getId(),
                null,
                data.project().getId(),
                Set.of(),
                created.version()
        );
        var updateResponse = restTemplate.exchange(
                "/api/tasks/{id}",
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest),
                TaskResponse.class,
                created.id()
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TaskResponse updated = updateResponse.getBody();
        assertThat(updated).isNotNull();
        assertThat(updated.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(updated.priority()).isEqualTo(TaskPriority.CRITICAL);
        assertThat(updated.assigneeId()).isNull();
        assertThat(updated.labelIds()).isEmpty();
        assertThat(updated.version()).isEqualTo(created.version() + 1);

        var staleResponse = restTemplate.exchange(
                "/api/tasks/{id}",
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest),
                String.class,
                created.id()
        );
        assertThat(staleResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(staleResponse.getBody()).contains("modified by another request");

        var deleteResponse = restTemplate.exchange(
                "/api/tasks/{id}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class,
                created.id()
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(taskRepository.count()).isZero();
    }

    @Test
    void shouldRejectDuplicateTaskKey() {
        TestData data = createTestData();
        assertThat(restTemplate.postForEntity(
                "/api/tasks",
                createRequest(data, "PLATFORM-1"),
                TaskResponse.class
        ).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var duplicateResponse = restTemplate.postForEntity(
                "/api/tasks",
                createRequest(data, "platform-1"),
                String.class
        );

        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateResponse.getBody()).contains("already exists");
        assertThat(taskRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectMissingRelatedResourceWithoutSavingTask() {
        User author = userRepository.saveAndFlush(
                new User("Author", "author@example.com", UserRole.TEAM_LEAD)
        );
        var response = restTemplate.postForEntity(
                "/api/tasks",
                new CreateTaskRequest(
                        "PLATFORM-1",
                        "Task",
                        null,
                        TaskStatus.TODO,
                        TaskPriority.MEDIUM,
                        author.getId(),
                        null,
                        999_999L,
                        Set.of()
                ),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(taskRepository.count()).isZero();
    }

    @Test
    void shouldValidateTaskEntity() {
        TestData data = createTestData();
        Task invalidTask = new Task(
                "bad",
                " ",
                null,
                null,
                null,
                data.author(),
                null,
                data.project(),
                Set.of()
        );

        Set<String> invalidFields = validator.validate(invalidTask).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(invalidFields).containsExactlyInAnyOrder("taskKey", "title", "status", "priority");
    }

    @Test
    void shouldRejectPageSizeAboveFiftyAndExposeOpenApi() {
        var invalidPage = restTemplate.getForEntity("/api/tasks?size=51", String.class);
        assertThat(invalidPage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        var openApi = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApi.getBody())
                .contains("/api/tasks")
                .contains("X-Total-Count")
                .contains("TaskResponse")
                .contains("ApiError");
    }

    private TestData createTestData() {
        User author = userRepository.saveAndFlush(
                new User("Author", "author@example.com", UserRole.TEAM_LEAD)
        );
        User assignee = userRepository.saveAndFlush(
                new User("Developer", "developer@example.com", UserRole.DEVELOPER)
        );
        Project project = projectRepository.saveAndFlush(
                new Project("Platform", "PLATFORM", null, ProjectStatus.ACTIVE)
        );
        Label label = labelRepository.saveAndFlush(new Label("Backend", "#112233"));
        return new TestData(author, assignee, project, label);
    }

    private CreateTaskRequest createRequest(TestData data, String taskKey) {
        return new CreateTaskRequest(
                taskKey,
                "First task",
                "Description",
                TaskStatus.TODO,
                TaskPriority.HIGH,
                data.author().getId(),
                data.assignee().getId(),
                data.project().getId(),
                Set.of(data.label().getId())
        );
    }

    private record TestData(User author, User assignee, Project project, Label label) {
    }
}
