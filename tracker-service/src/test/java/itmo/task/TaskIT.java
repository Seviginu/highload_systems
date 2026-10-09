package itmo.task;

import itmo.label.entity.Label;
import itmo.label.repository.LabelRepository;
import itmo.project.entity.Project;
import itmo.project.entity.ProjectMember;
import itmo.project.entity.ProjectStatus;
import itmo.project.repository.ProjectMemberRepository;
import itmo.project.repository.ProjectRepository;
import itmo.task.dto.CreateTaskRequest;
import itmo.task.dto.MoveTaskRequest;
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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "logging.file.name=target/task-it.log",
                "spring.jpa.properties.hibernate.session_factory.statement_inspector=itmo.task.SqlStatementInspector"
        }
)
class TaskIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

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

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanDatabase() {
        taskCache().clear();
        taskRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        labelRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldCacheTaskAndKeepCacheConsistentAfterWrites() {
        TestData data = createTestData();
        TaskResponse created = restTemplate.postForEntity(
                "/api/v1/tasks",
                createRequest(data, "PLATFORM-1"),
                TaskResponse.class
        ).getBody();
        assertThat(created).isNotNull();

        TaskResponse cachedAfterCreate = taskCache().get(created.id(), TaskResponse.class);
        assertThat(cachedAfterCreate).isNotNull();
        assertThat(cachedAfterCreate.title()).isEqualTo("First task");
        assertThat(redisTemplate.getExpire("tasks::" + created.id(), TimeUnit.SECONDS))
                .isBetween(1L, 600L);

        taskCache().clear();
        jdbcTemplate.update("UPDATE tasks SET title = ? WHERE id = ?", "Loaded from database", created.id());
        TaskResponse loadedFromDatabase = restTemplate.getForObject(
                "/api/v1/tasks/{id}",
                TaskResponse.class,
                created.id()
        );
        assertThat(loadedFromDatabase).isNotNull();
        assertThat(loadedFromDatabase.title()).isEqualTo("Loaded from database");
        assertThat(taskCache().get(created.id(), TaskResponse.class)).isNotNull();

        jdbcTemplate.update("UPDATE tasks SET title = ? WHERE id = ?", "Changed outside service", created.id());
        TaskResponse cachedRead = restTemplate.getForObject(
                "/api/v1/tasks/{id}",
                TaskResponse.class,
                created.id()
        );
        assertThat(cachedRead).isNotNull();
        assertThat(cachedRead.title()).isEqualTo("Loaded from database");

        var updateResponse = restTemplate.exchange(
                "/api/v1/tasks/{id}",
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateTaskRequest(
                        created.taskKey(),
                        "Updated through service",
                        created.description(),
                        created.status(),
                        created.priority(),
                        created.authorId(),
                        created.assigneeId(),
                        created.projectId(),
                        Set.copyOf(created.labelIds()),
                        created.version()
                )),
                TaskResponse.class,
                created.id()
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TaskResponse cachedAfterUpdate = taskCache().get(created.id(), TaskResponse.class);
        assertThat(cachedAfterUpdate).isNotNull();
        assertThat(cachedAfterUpdate.title()).isEqualTo("Updated through service");

        var deleteResponse = restTemplate.exchange(
                "/api/v1/tasks/{id}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class,
                created.id()
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(taskCache().get(created.id())).isNull();
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
                "/api/v1/tasks",
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

        var listResponse = restTemplate.getForEntity("/api/v1/tasks?page=0&size=20", TaskResponse[].class);
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
                "/api/v1/tasks/{id}",
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
                "/api/v1/tasks/{id}",
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest),
                String.class,
                created.id()
        );
        assertThat(staleResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(staleResponse.getBody()).contains("modified by another request");

        var deleteResponse = restTemplate.exchange(
                "/api/v1/tasks/{id}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class,
                created.id()
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(taskRepository.count()).isZero();
    }

    @Test
    void shouldFilterTasksByRelationsStatusAndPriority() {
        TestData data = createTestData();
        TaskResponse matching = restTemplate.postForEntity(
                "/api/v1/tasks",
                createRequest(data, "PLATFORM-1"),
                TaskResponse.class
        ).getBody();
        TaskResponse differentState = restTemplate.postForEntity(
                "/api/v1/tasks",
                new CreateTaskRequest(
                        "PLATFORM-2",
                        "Completed task",
                        null,
                        TaskStatus.DONE,
                        TaskPriority.LOW,
                        data.author().getId(),
                        data.assignee().getId(),
                        data.project().getId(),
                        Set.of(data.label().getId())
                ),
                TaskResponse.class
        ).getBody();
        assertThat(matching).isNotNull();
        assertThat(differentState).isNotNull();

        User anotherAuthor = userRepository.saveAndFlush(
                new User("Another author", "another-author@example.com", UserRole.TEAM_LEAD)
        );
        User anotherAssignee = userRepository.saveAndFlush(
                new User("Another developer", "another-developer@example.com", UserRole.DEVELOPER)
        );
        Project anotherProject = projectRepository.saveAndFlush(
                new Project("Mobile", "MOBILE", null, ProjectStatus.ACTIVE)
        );
        Label anotherLabel = labelRepository.saveAndFlush(new Label("Mobile", "#445566"));
        restTemplate.postForEntity(
                "/api/v1/tasks",
                new CreateTaskRequest(
                        "MOBILE-1",
                        "Another task",
                        null,
                        TaskStatus.TODO,
                        TaskPriority.HIGH,
                        anotherAuthor.getId(),
                        anotherAssignee.getId(),
                        anotherProject.getId(),
                        Set.of(anotherLabel.getId())
                ),
                TaskResponse.class
        );

        String combinedFilter = "/api/v1/tasks?projectId=%d&authorId=%d&assigneeId=%d&labelId=%d"
                .formatted(
                        data.project().getId(),
                        data.author().getId(),
                        data.assignee().getId(),
                        data.label().getId()
                ) + "&status=TODO&priority=HIGH&page=0&size=20";
        var filtered = restTemplate.getForEntity(combinedFilter, TaskResponse[].class);

        assertThat(filtered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(filtered.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        assertThat(filtered.getBody()).extracting(TaskResponse::id).containsExactly(matching.id());

        var byLabel = restTemplate.getForEntity(
                "/api/v1/tasks?labelId={labelId}",
                TaskResponse[].class,
                data.label().getId()
        );
        assertThat(byLabel.getHeaders().getFirst("X-Total-Count")).isEqualTo("2");
        assertThat(byLabel.getBody()).extracting(TaskResponse::id)
                .containsExactly(matching.id(), differentState.id());
    }

    @Test
    void shouldRejectDuplicateTaskKey() {
        TestData data = createTestData();
        assertThat(restTemplate.postForEntity(
                "/api/v1/tasks",
                createRequest(data, "PLATFORM-1"),
                TaskResponse.class
        ).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var duplicateResponse = restTemplate.postForEntity(
                "/api/v1/tasks",
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
                "/api/v1/tasks",
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
    void shouldReadTaskFeedByCursorWithoutCountQuery() {
        TestData data = createTestData();
        TaskResponse first = restTemplate.postForEntity(
                "/api/v1/tasks", createRequest(data, "PLATFORM-1"), TaskResponse.class
        ).getBody();
        TaskResponse second = restTemplate.postForEntity(
                "/api/v1/tasks", createRequest(data, "PLATFORM-2"), TaskResponse.class
        ).getBody();
        TaskResponse third = restTemplate.postForEntity(
                "/api/v1/tasks", createRequest(data, "PLATFORM-3"), TaskResponse.class
        ).getBody();
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(third).isNotNull();

        SqlStatementInspector.clear();
        var firstPage = restTemplate.getForEntity("/api/v1/tasks/feed?limit=2", TaskResponse[].class);

        assertThat(firstPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstPage.getBody()).extracting(TaskResponse::id)
                .containsExactly(first.id(), second.id());
        assertThat(firstPage.getHeaders().getFirst("X-Next-Cursor"))
                .isEqualTo(String.valueOf(second.id()));
        assertThat(SqlStatementInspector.statements())
                .noneMatch(sql -> sql.toLowerCase().contains("count("));

        var lastPage = restTemplate.getForEntity(
                "/api/v1/tasks/feed?afterId={afterId}&limit=2",
                TaskResponse[].class,
                second.id()
        );

        assertThat(lastPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lastPage.getBody()).extracting(TaskResponse::id).containsExactly(third.id());
        assertThat(lastPage.getHeaders().containsKey("X-Next-Cursor")).isFalse();
    }

    @Test
    void shouldMoveTaskAtomicallyAndRejectNonMemberAssignee() {
        TestData data = createTestData();
        Project targetProject = projectRepository.saveAndFlush(
                new Project("Mobile", "MOBILE", null, ProjectStatus.ACTIVE)
        );
        Project rejectedProject = projectRepository.saveAndFlush(
                new Project("Analytics", "ANALYTICS", null, ProjectStatus.ACTIVE)
        );
        Label targetLabel = labelRepository.saveAndFlush(new Label("Mobile", "#445566"));
        projectMemberRepository.saveAndFlush(new ProjectMember(targetProject, data.assignee()));
        TaskResponse created = restTemplate.postForEntity(
                "/api/v1/tasks",
                createRequest(data, "PLATFORM-1"),
                TaskResponse.class
        ).getBody();
        assertThat(created).isNotNull();

        var moveResponse = restTemplate.postForEntity(
                "/api/v1/tasks/{id}/move",
                new MoveTaskRequest(
                        targetProject.getId(),
                        data.assignee().getId(),
                        Set.of(targetLabel.getId()),
                        created.version()
                ),
                TaskResponse.class,
                created.id()
        );

        assertThat(moveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TaskResponse moved = moveResponse.getBody();
        assertThat(moved).isNotNull();
        assertThat(moved.projectId()).isEqualTo(targetProject.getId());
        assertThat(moved.assigneeId()).isEqualTo(data.assignee().getId());
        assertThat(moved.labelIds()).containsExactly(targetLabel.getId());
        assertThat(moved.version()).isEqualTo(created.version() + 1);
        TaskResponse cachedAfterMove = taskCache().get(created.id(), TaskResponse.class);
        assertThat(cachedAfterMove).isNotNull();
        assertThat(cachedAfterMove.projectId()).isEqualTo(targetProject.getId());

        var rejectedResponse = restTemplate.postForEntity(
                "/api/v1/tasks/{id}/move",
                new MoveTaskRequest(
                        rejectedProject.getId(),
                        data.assignee().getId(),
                        Set.of(data.label().getId()),
                        moved.version()
                ),
                String.class,
                created.id()
        );

        assertThat(rejectedResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rejectedResponse.getBody()).contains("not an active member");
        Task persisted = taskRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.getProject().getId()).isEqualTo(targetProject.getId());
        assertThat(persisted.getAssignee().getId()).isEqualTo(data.assignee().getId());
        assertThat(persisted.getLabels()).extracting(Label::getId).containsExactly(targetLabel.getId());
        assertThat(persisted.getVersion()).isEqualTo(moved.version());
    }

    @Test
    void shouldRejectProjectChangeThroughRegularUpdate() {
        TestData data = createTestData();
        Project targetProject = projectRepository.saveAndFlush(
                new Project("Mobile", "MOBILE", null, ProjectStatus.ACTIVE)
        );
        TaskResponse created = restTemplate.postForEntity(
                "/api/v1/tasks",
                createRequest(data, "PLATFORM-1"),
                TaskResponse.class
        ).getBody();
        assertThat(created).isNotNull();

        var response = restTemplate.exchange(
                "/api/v1/tasks/{id}",
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateTaskRequest(
                        created.taskKey(),
                        created.title(),
                        created.description(),
                        created.status(),
                        created.priority(),
                        created.authorId(),
                        created.assigneeId(),
                        targetProject.getId(),
                        Set.copyOf(created.labelIds()),
                        created.version()
                )),
                String.class,
                created.id()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("move operation");
        Task persisted = taskRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.getProject().getId()).isEqualTo(data.project().getId());
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
        var invalidPage = restTemplate.getForEntity("/api/v1/tasks?size=51", String.class);
        assertThat(invalidPage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var invalidFilter = restTemplate.getForEntity("/api/v1/tasks?labelId=0", String.class);
        assertThat(invalidFilter.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var invalidFeed = restTemplate.getForEntity(
                "/api/v1/tasks/feed?afterId=-1&limit=51",
                String.class
        );
        assertThat(invalidFeed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        var openApi = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApi.getBody())
                .contains("/api/v1/users")
                .contains("/api/v1/projects")
                .contains("/api/v1/projects/{projectId}/members")
                .contains("/api/v1/labels")
                .contains("/api/v1/tasks")
                .contains("/api/v1/tasks/feed")
                .contains("/api/v1/tasks/{id}/move")
                .contains("X-Total-Count")
                .contains("X-Next-Cursor")
                .contains("UserResponse")
                .contains("ProjectResponse")
                .contains("ProjectMemberResponse")
                .contains("LabelResponse")
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

    private Cache taskCache() {
        return Objects.requireNonNull(cacheManager.getCache("tasks"));
    }

    private record TestData(User author, User assignee, Project project, Label label) {
    }
}
