package itmo.task;

import itmo.tracker.application.model.TaskView;
import itmo.tracker.adapter.out.persistence.LabelEntity;
import itmo.tracker.adapter.out.persistence.LabelRepository;
import itmo.tracker.adapter.out.persistence.ProjectEntity;
import itmo.tracker.adapter.out.persistence.ProjectMemberEntity;
import itmo.tracker.domain.ProjectStatus;
import itmo.tracker.adapter.out.persistence.ProjectMemberRepository;
import itmo.tracker.adapter.out.persistence.ProjectRepository;
import itmo.tracker.adapter.in.web.CreateTaskRequest;
import itmo.tracker.adapter.in.web.MoveTaskRequest;
import itmo.tracker.adapter.in.web.TaskResponse;
import itmo.tracker.adapter.in.web.UpdateTaskRequest;
import itmo.tracker.adapter.out.persistence.TaskEntity;
import itmo.tracker.domain.TaskPriority;
import itmo.tracker.domain.TaskStatus;
import itmo.tracker.adapter.out.persistence.TaskRepository;
import itmo.support.TestUser;
import itmo.support.UserApiStub;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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
@ActiveProfiles("test")
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

    private static final UserApiStub USERS = new UserApiStub();

    @DynamicPropertySource
    static void userClientProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.user-service.url", USERS::url);
        registry.add("spring.cloud.openfeign.client.config.user-service.readTimeout", () -> 200);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Validator validator;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private long currentUserId = 1;

    @BeforeEach
    void cleanDatabase() {
        currentUserId = 1;
        restTemplate.getRestTemplate().setInterceptors(java.util.List.of((request, body, execution) -> {
            request.getHeaders().set("Authorization", itmo.security.TestTokens.bearer(currentUserId, "ADMIN"));
            return execution.execute(request, body);
        }));
        taskCache().clear();
        taskRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        labelRepository.deleteAll();
        USERS.clear();
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

        TaskView cachedAfterCreate = taskCache().get(created.id(), TaskView.class);
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
        assertThat(taskCache().get(created.id(), TaskView.class)).isNotNull();

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
                        created.assigneeId(),
                        created.projectId(),
                        Set.copyOf(created.labelIds()),
                        created.version()
                )),
                TaskResponse.class,
                created.id()
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TaskView cachedAfterUpdate = taskCache().get(created.id(), TaskView.class);
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

        TaskEntity persisted = taskRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.getProject().getId()).isEqualTo(data.project().getId());
        assertThat(persisted.getLabels()).extracting(LabelEntity::getId).containsExactly(data.label().getId());

        var listResponse = restTemplate.getForEntity("/api/v1/tasks?page=0&size=20", TaskResponse[].class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");

        UpdateTaskRequest updateRequest = new UpdateTaskRequest(
                "platform-1",
                "Updated task",
                null,
                TaskStatus.IN_PROGRESS,
                TaskPriority.CRITICAL,
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
                        data.assignee().getId(),
                        data.project().getId(),
                        Set.of(data.label().getId())
                ),
                TaskResponse.class
        ).getBody();
        assertThat(matching).isNotNull();
        assertThat(differentState).isNotNull();

        TestUser anotherAuthor = USERS.create(
                new TestUser("TEAM_LEAD")
        );
        TestUser anotherAssignee = USERS.create(
                new TestUser("DEVELOPER")
        );
        ProjectEntity anotherProject = projectRepository.saveAndFlush(
                new ProjectEntity("Mobile", "MOBILE", null, ProjectStatus.ACTIVE)
        );
        projectMemberRepository.saveAndFlush(new ProjectMemberEntity(anotherProject, anotherAssignee.getId()));
        currentUserId = anotherAuthor.getId();
        LabelEntity anotherLabel = labelRepository.saveAndFlush(new LabelEntity("Mobile", "#445566"));
        restTemplate.postForEntity(
                "/api/v1/tasks",
                new CreateTaskRequest(
                        "MOBILE-1",
                        "Another task",
                        null,
                        TaskStatus.TODO,
                        TaskPriority.HIGH,
                        anotherAssignee.getId(),
                        anotherProject.getId(),
                        Set.of(anotherLabel.getId())
                ),
                TaskResponse.class
        );

        currentUserId = data.author().getId();
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
        TestUser author = USERS.create(
                new TestUser("TEAM_LEAD")
        );
        var response = restTemplate.postForEntity(
                "/api/v1/tasks",
                new CreateTaskRequest(
                        "PLATFORM-1",
                        "Task",
                        null,
                        TaskStatus.TODO,
                        TaskPriority.MEDIUM,
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
        ProjectEntity targetProject = projectRepository.saveAndFlush(
                new ProjectEntity("Mobile", "MOBILE", null, ProjectStatus.ACTIVE)
        );
        ProjectEntity rejectedProject = projectRepository.saveAndFlush(
                new ProjectEntity("Analytics", "ANALYTICS", null, ProjectStatus.ACTIVE)
        );
        LabelEntity targetLabel = labelRepository.saveAndFlush(new LabelEntity("Mobile", "#445566"));
        projectMemberRepository.saveAndFlush(new ProjectMemberEntity(targetProject, data.assignee().getId()));
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
        TaskView cachedAfterMove = taskCache().get(created.id(), TaskView.class);
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
        TaskEntity persisted = taskRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.getProject().getId()).isEqualTo(targetProject.getId());
        assertThat(persisted.getAssigneeId()).isEqualTo(data.assignee().getId());
        assertThat(persisted.getLabels()).extracting(LabelEntity::getId).containsExactly(targetLabel.getId());
        assertThat(persisted.getVersion()).isEqualTo(moved.version());
    }

    @Test
    void shouldRejectProjectChangeThroughRegularUpdate() {
        TestData data = createTestData();
        ProjectEntity targetProject = projectRepository.saveAndFlush(
                new ProjectEntity("Mobile", "MOBILE", null, ProjectStatus.ACTIVE)
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
        TaskEntity persisted = taskRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.getProject().getId()).isEqualTo(data.project().getId());
    }

    @Test
    void shouldValidateTaskEntity() {
        TestData data = createTestData();
        TaskEntity invalidTask = new TaskEntity(
                "bad",
                " ",
                null,
                null,
                null,
                data.author().getId(),
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
                .contains("\"servers\":[{\"url\":\"/\"}]")
                .contains("/api/v1/projects")
                .contains("/api/v1/projects/{projectId}/members")
                .contains("/api/v1/labels")
                .contains("/api/v1/tasks")
                .contains("/api/v1/tasks/feed")
                .contains("/api/v1/tasks/{id}/move")
                .contains("X-Total-Count")
                .contains("X-Next-Cursor")
                .contains("ProjectResponse")
                .contains("ProjectMemberResponse")
                .contains("LabelResponse")
                .contains("TaskResponse")
                .contains("ApiError");
    }

    @Test
    void shouldKeepHistoricalReferencesAndAllowEditingAfterUsersAreDeleted() {
        TestData data = createTestData();
        TaskResponse created = restTemplate.postForEntity("/api/v1/tasks", createRequest(data, "PLATFORM-1"),
                TaskResponse.class).getBody();
        assertThat(created).isNotNull();
        USERS.delete(data.author().getId());
        USERS.delete(data.assignee().getId());
        taskCache().clear();
        var read = restTemplate.getForEntity("/api/v1/tasks/{id}", TaskResponse.class, created.id());
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody().authorId()).isEqualTo(data.author().getId());
        assertThat(read.getBody().assigneeId()).isEqualTo(data.assignee().getId());
        var update = restTemplate.exchange("/api/v1/tasks/{id}", HttpMethod.PUT,
                new HttpEntity<>(new UpdateTaskRequest(created.taskKey(), "Edited history", created.description(),
                        created.status(), created.priority(), created.assigneeId(), created.projectId(),
                        Set.copyOf(created.labelIds()), created.version())), TaskResponse.class, created.id());
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(update.getBody().title()).isEqualTo("Edited history");
        assertThat(update.getBody().authorId()).isEqualTo(data.author().getId());
        assertThat(update.getBody().assigneeId()).isEqualTo(data.assignee().getId());
        var rejected = restTemplate.postForEntity("/api/v1/tasks", createRequest(data, "PLATFORM-2"), String.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(taskRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldNotWriteTaskWhenUserServiceIsUnavailable() {
        TestData data = createTestData();
        USERS.unavailable(true);
        var response = restTemplate.postForEntity("/api/v1/tasks", createRequest(data, "PLATFORM-1"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(taskRepository.count()).isZero();
        assertThat(restTemplate.getForEntity("/api/v1/tasks", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private TestData createTestData() {
        TestUser author = USERS.create(
                new TestUser("TEAM_LEAD")
        );
        TestUser assignee = USERS.create(
                new TestUser("DEVELOPER")
        );
        ProjectEntity project = projectRepository.saveAndFlush(
                new ProjectEntity("Platform", "PLATFORM", null, ProjectStatus.ACTIVE)
        );
        currentUserId = author.getId();
        projectMemberRepository.saveAndFlush(new ProjectMemberEntity(project, assignee.getId()));
        LabelEntity label = labelRepository.saveAndFlush(new LabelEntity("Backend", "#112233"));
        return new TestData(author, assignee, project, label);
    }

    private CreateTaskRequest createRequest(TestData data, String taskKey) {
        return new CreateTaskRequest(
                taskKey,
                "First task",
                "Description",
                TaskStatus.TODO,
                TaskPriority.HIGH,
                data.assignee().getId(),
                data.project().getId(),
                Set.of(data.label().getId())
        );
    }

    private Cache taskCache() {
        return Objects.requireNonNull(cacheManager.getCache("tasks"));
    }

    private record TestData(TestUser author, TestUser assignee, ProjectEntity project, LabelEntity label) {
    }
}
