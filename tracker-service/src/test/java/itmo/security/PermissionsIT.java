package itmo.security;

import itmo.project.entity.Project;
import itmo.project.entity.ProjectMember;
import itmo.project.entity.ProjectStatus;
import itmo.project.repository.ProjectMemberRepository;
import itmo.project.repository.ProjectRepository;
import itmo.support.TestUser;
import itmo.support.UserApiStub;
import itmo.task.dto.TaskResponse;
import itmo.task.entity.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PermissionsIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static final UserApiStub USERS = new UserApiStub();

    @DynamicPropertySource
    static void client(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.user-service.url", USERS::url);
    }

    @Autowired private WebTestClient http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProjectRepository projects;
    @Autowired private ProjectMemberRepository members;
    @Autowired private CacheManager cache;
    private TestUser lead;
    private TestUser developer;
    private TestUser other;
    private TestUser outsider;
    private Project project;
    private Project target;

    @BeforeEach
    void prepare() {
        cache.getCache("tasks").clear();
        jdbc.update("DELETE FROM task_labels");
        jdbc.update("DELETE FROM tasks");
        jdbc.update("DELETE FROM project_members");
        jdbc.update("DELETE FROM projects");
        jdbc.update("DELETE FROM labels");
        USERS.clear();
        lead = USERS.create(new TestUser("TEAM_LEAD"));
        developer = USERS.create(new TestUser("DEVELOPER"));
        other = USERS.create(new TestUser("DEVELOPER"));
        outsider = USERS.create(new TestUser("TEAM_LEAD"));
        project = projects.saveAndFlush(new Project("Platform", "PLATFORM", null, ProjectStatus.ACTIVE));
        target = projects.saveAndFlush(new Project("Target", "TARGET", null, ProjectStatus.ACTIVE));
        members.saveAndFlush(new ProjectMember(project, lead.getId()));
        members.saveAndFlush(new ProjectMember(project, developer.getId()));
        members.saveAndFlush(new ProjectMember(project, other.getId()));
        members.saveAndFlush(new ProjectMember(target, outsider.getId()));
    }

    @Test
    void shouldAssignCurrentLeadAndRestrictProjectMutations() {
        var create = Map.of("name", "New", "code", "NEW", "status", "ACTIVE", "teamLeadId", lead.getId());
        Long created = as(lead).post().uri("/api/v1/projects").bodyValue(create).exchange().expectStatus().isCreated()
                .expectBody(itmo.project.dto.ProjectResponse.class).returnResult().getResponseBody().id();
        assertThat(members.existsByProjectIdAndUserIdAndActiveTrue(created, lead.getId())).isTrue();
        as(outsider).post().uri("/api/v1/projects").bodyValue(create).exchange().expectStatus().isForbidden();
        as(developer).post().uri("/api/v1/projects").bodyValue(create).exchange().expectStatus().isForbidden();
        var update = Map.of("name", "Updated", "code", "PLATFORM", "status", "ACTIVE");
        as(outsider).put().uri("/api/v1/projects/{id}", project.getId()).bodyValue(update).exchange().expectStatus().isForbidden();
        as(developer).delete().uri("/api/v1/projects/{id}", project.getId()).exchange().expectStatus().isForbidden();
        as(lead).put().uri("/api/v1/projects/{id}", project.getId()).bodyValue(update).exchange().expectStatus().isOk();
        admin().delete().uri("/api/v1/projects/{id}", created).exchange().expectStatus().isNoContent();
        as(other).get().uri("/api/v1/projects/{id}", target.getId()).exchange().expectStatus().isOk();
    }

    @Test
    void shouldRestrictAddingAndDeactivatingProjectMembers() {
        as(outsider).post().uri("/api/v1/projects/{id}/members", project.getId()).bodyValue(Map.of("userId", outsider.getId()))
                .exchange().expectStatus().isForbidden();
        as(developer).post().uri("/api/v1/projects/{id}/members", project.getId()).bodyValue(Map.of("userId", outsider.getId()))
                .exchange().expectStatus().isForbidden();
        as(lead).post().uri("/api/v1/projects/{id}/members", project.getId()).bodyValue(Map.of("userId", outsider.getId()))
                .exchange().expectStatus().isCreated();
        Long memberId = members.findByProjectIdAndUserId(project.getId(), other.getId()).orElseThrow().getId();
        as(developer).delete().uri("/api/v1/projects/{id}/members/{member}", project.getId(), memberId)
                .exchange().expectStatus().isForbidden();
        as(lead).delete().uri("/api/v1/projects/{id}/members/{member}", project.getId(), memberId)
                .exchange().expectStatus().isNoContent();
        assertThat(members.existsByProjectIdAndUserIdAndActiveTrue(project.getId(), other.getId())).isFalse();
    }

    @Test
    void shouldDeriveAuthorFromJwtAndPreserveItDuringFullUpdate() {
        var create = taskBody();
        create.put("authorId", other.getId());
        TaskResponse task = as(lead).post().uri("/api/v1/tasks").bodyValue(create).exchange().expectStatus().isCreated()
                .expectBody(TaskResponse.class).returnResult().getResponseBody();
        assertThat(task.authorId()).isEqualTo(lead.getId());
        var update = taskBody();
        update.put("title", "Updated");
        update.put("authorId", other.getId());
        update.put("version", task.version());
        admin().put().uri("/api/v1/tasks/{id}", task.id()).bodyValue(update).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.authorId").isEqualTo(lead.getId());
        assertThat(jdbc.queryForObject("SELECT author_id FROM tasks WHERE id = ?", Long.class, task.id())).isEqualTo(lead.getId());
    }

    @Test
    void shouldAllowOnlyStatusChangesForAssignedActiveDeveloperAndKeepCacheConsistent() {
        TaskResponse task = task();
        var malicious = Map.of("status", "IN_PROGRESS", "version", task.version(), "assigneeId", other.getId(), "projectId", target.getId());
        as(developer).patch().uri("/api/v1/tasks/{id}/status", task.id()).bodyValue(malicious).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("IN_PROGRESS")
                .jsonPath("$.assigneeId").isEqualTo(developer.getId()).jsonPath("$.projectId").isEqualTo(project.getId());
        TaskResponse updated = cache.getCache("tasks").get(task.id(), TaskResponse.class);
        assertThat(updated.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(updated.version()).isEqualTo(task.version() + 1);
        as(other).patch().uri("/api/v1/tasks/{id}/status", task.id())
                .bodyValue(Map.of("status", "DONE", "version", updated.version())).exchange().expectStatus().isForbidden();
        as(outsider).patch().uri("/api/v1/tasks/{id}/status", task.id())
                .bodyValue(Map.of("status", "DONE", "version", updated.version())).exchange().expectStatus().isForbidden();
        as(developer).patch().uri("/api/v1/tasks/{id}/status", task.id())
                .bodyValue(Map.of("status", "DONE", "version", task.version())).exchange().expectStatus().isEqualTo(409);
        assertThat(cache.getCache("tasks").get(task.id(), TaskResponse.class)).isEqualTo(updated);
        jdbc.update("UPDATE project_members SET active = false WHERE project_id = ? AND user_id = ?", project.getId(), developer.getId());
        as(developer).patch().uri("/api/v1/tasks/{id}/status", task.id())
                .bodyValue(Map.of("status", "DONE", "version", updated.version())).exchange().expectStatus().isForbidden();
        assertThat(jdbc.queryForObject("SELECT status FROM tasks WHERE id = ?", String.class, task.id())).isEqualTo("IN_PROGRESS");
        as(lead).patch().uri("/api/v1/tasks/{id}/status", task.id())
                .bodyValue(Map.of("status", "DONE", "version", updated.version())).exchange().expectStatus().isOk();
    }

    @Test
    void shouldDenyDeveloperFullWritesAndOutsiderLeadWithoutDatabaseOrCacheChanges() {
        TaskResponse task = task();
        var update = taskBody(); update.put("version", task.version());
        for (TestUser user : new TestUser[]{developer, outsider}) {
            as(user).post().uri("/api/v1/tasks").bodyValue(taskBody()).exchange().expectStatus().isForbidden();
            as(user).put().uri("/api/v1/tasks/{id}", task.id()).bodyValue(update).exchange().expectStatus().isForbidden();
            as(user).post().uri("/api/v1/tasks/{id}/move", task.id())
                    .bodyValue(Map.of("projectId", target.getId(), "labelIds", Set.of(), "version", task.version()))
                    .exchange().expectStatus().isForbidden();
            as(user).delete().uri("/api/v1/tasks/{id}", task.id()).exchange().expectStatus().isForbidden();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM tasks WHERE id = ?", Long.class, task.id())).isEqualTo(task.version());
        assertThat(cache.getCache("tasks").get(task.id(), TaskResponse.class)).isEqualTo(task);
    }

    @Test
    void shouldRequireLeadMembershipInBothProjectsForMove() {
        TaskResponse task = task();
        var move = Map.of("projectId", target.getId(), "labelIds", Set.of(), "version", task.version());
        as(lead).post().uri("/api/v1/tasks/{id}/move", task.id()).bodyValue(move).exchange().expectStatus().isForbidden();
        assertThat(jdbc.queryForObject("SELECT project_id FROM tasks WHERE id = ?", Long.class, task.id())).isEqualTo(project.getId());
        members.saveAndFlush(new ProjectMember(target, lead.getId()));
        as(lead).post().uri("/api/v1/tasks/{id}/move", task.id()).bodyValue(move).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.projectId").isEqualTo(target.getId());
    }

    @Test
    void shouldRejectInactiveAssigneeOnCreateAndUpdateAndValidateStatusPatch() {
        TaskResponse task = task();
        jdbc.update("UPDATE project_members SET active = false WHERE project_id = ? AND user_id = ?", project.getId(), developer.getId());
        var create = taskBody(); create.put("taskKey", "PLATFORM-2");
        admin().post().uri("/api/v1/tasks").bodyValue(create).exchange().expectStatus().isEqualTo(409);
        var update = taskBody(); update.put("version", task.version());
        admin().put().uri("/api/v1/tasks/{id}", task.id()).bodyValue(update).exchange().expectStatus().isEqualTo(409);
        admin().patch().uri("/api/v1/tasks/{id}/status", task.id()).bodyValue(Map.of("status", "DONE"))
                .exchange().expectStatus().isBadRequest();
        admin().patch().uri("/api/v1/tasks/{id}/status", task.id()).bodyValue(Map.of("status", "DONE", "version", -1))
                .exchange().expectStatus().isBadRequest();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks", Long.class)).isEqualTo(1);
    }

    @Test
    void shouldRestrictGlobalLabelMutationsToSupervisor() {
        var body = Map.of("name", "Backend", "color", "#112233");
        for (TestUser user : new TestUser[]{lead, developer}) {
            as(user).post().uri("/api/v1/labels").bodyValue(body).exchange().expectStatus().isForbidden();
        }
        var label = admin().post().uri("/api/v1/labels").bodyValue(body).exchange().expectStatus().isCreated()
                .expectBody(itmo.label.dto.LabelResponse.class).returnResult().getResponseBody();
        as(lead).put().uri("/api/v1/labels/{id}", label.id()).bodyValue(body).exchange().expectStatus().isForbidden();
        as(developer).delete().uri("/api/v1/labels/{id}", label.id()).exchange().expectStatus().isForbidden();
        admin().delete().uri("/api/v1/labels/{id}", label.id()).exchange().expectStatus().isNoContent();
    }

    private TaskResponse task() {
        return admin().post().uri("/api/v1/tasks").bodyValue(taskBody()).exchange().expectStatus().isCreated()
                .expectBody(TaskResponse.class).returnResult().getResponseBody();
    }

    private Map<String, Object> taskBody() {
        return new HashMap<>(Map.of("taskKey", "PLATFORM-1", "title", "Task", "status", "TODO", "priority", "MEDIUM",
                "assigneeId", developer.getId(), "projectId", project.getId(), "labelIds", Set.of()));
    }

    private WebTestClient as(TestUser user) {
        return http.mutate().defaultHeader("Authorization", TestTokens.bearer(user.getId(), user.getRole())).build();
    }

    private WebTestClient admin() {
        return http.mutate().defaultHeader("Authorization", TestTokens.bearer()).build();
    }
}
