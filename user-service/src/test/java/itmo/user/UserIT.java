package itmo.user;

import itmo.user.dto.CreateUserRequest;
import itmo.user.dto.UpdateUserRequest;
import itmo.user.dto.UserResponse;
import itmo.user.entity.User;
import itmo.user.entity.UserRole;
import itmo.user.repository.UserRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(), POSTGRES.getMappedPort(5432), POSTGRES.getDatabaseName()));
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.url", POSTGRES::getJdbcUrl);
        registry.add("spring.liquibase.user", POSTGRES::getUsername);
        registry.add("spring.liquibase.password", POSTGRES::getPassword);
    }

    @Autowired
    private WebTestClient client;
    @Autowired
    private UserRepository users;
    @Autowired
    private DatabaseClient database;

    @BeforeEach
    void cleanDatabase() {
        users.deleteAll().block();
    }

    @Test
    void shouldApplyLiquibaseAndStoreStringRoleAndTimestamps() {
        var user = create("Alice", "Alice@Example.COM", UserRole.DEVELOPER);
        assertThat(user.email()).isEqualTo("alice@example.com");
        assertThat(user.createdAt()).isNotNull();
        assertThat(user.updatedAt()).isAfterOrEqualTo(user.createdAt());
        var row = database.sql("SELECT role, deleted FROM users WHERE id = :id")
                .bind("id", user.id()).fetch().one().block();
        assertThat(row).containsEntry("role", "DEVELOPER").containsEntry("deleted", false);
        var count = database.sql("SELECT count(*) AS count FROM databasechangelog WHERE author = 'user-service'")
                .fetch().one().block();
        assertThat(count.get("count")).isEqualTo(1L);
    }

    @Test
    void shouldPerformReactiveCrudAndReturnLocation() {
        var created = client.post().uri("/api/v1/users")
                .bodyValue(new CreateUserRequest(" Alice ", "alice@example.com", UserRole.DEVELOPER))
                .exchange().expectStatus().isCreated().expectHeader().exists("Location")
                .expectBody(UserResponse.class).returnResult().getResponseBody();
        assertThat(created.name()).isEqualTo("Alice");
        client.get().uri("/api/v1/users/{id}", created.id()).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo(created.id());
        client.put().uri("/api/v1/users/{id}", created.id())
                .bodyValue(new UpdateUserRequest("Alice Lead", "lead@example.com", UserRole.TEAM_LEAD))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.role").isEqualTo("TEAM_LEAD");
        client.delete().uri("/api/v1/users/{id}", created.id()).exchange().expectStatus().isNoContent();
        client.get().uri("/api/v1/users/{id}", created.id()).exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.status").isEqualTo(404);
        client.delete().uri("/api/v1/users/{id}", created.id()).exchange().expectStatus().isNotFound();
    }

    @Test
    void shouldHideDeletedUsersAndReserveTheirEmails() {
        var removed = create("Removed", "removed@example.com", UserRole.TEAM_LEAD);
        var active = create("Active", "active@example.com", UserRole.DEVELOPER);
        client.delete().uri("/api/v1/users/{id}", removed.id()).exchange().expectStatus().isNoContent();
        client.get().uri("/api/v1/users?page=0&size=1").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Total-Count", "1")
                .expectBody().jsonPath("$.length()").isEqualTo(1).jsonPath("$[0].id").isEqualTo(active.id());
        client.post().uri("/internal/users/resolve").bodyValue(Map.of("ids", new Long[]{removed.id(), active.id()}))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo(active.id());
        client.post().uri("/api/v1/users")
                .bodyValue(new CreateUserRequest("Duplicate", "REMOVED@EXAMPLE.COM", UserRole.ADMIN))
                .exchange().expectStatus().isEqualTo(409).expectBody().jsonPath("$.status").isEqualTo(409);
        assertThat(users.findById(removed.id()).block().isDeleted()).isTrue();
        assertThat(users.count().block()).isEqualTo(2);
    }

    @Test
    void shouldRejectDuplicateEmailDuringUpdate() {
        var first = create("First", "first@example.com", UserRole.DEVELOPER);
        create("Second", "second@example.com", UserRole.ADMIN);
        client.put().uri("/api/v1/users/{id}", first.id())
                .bodyValue(new UpdateUserRequest("First", "SECOND@EXAMPLE.COM", UserRole.DEVELOPER))
                .exchange().expectStatus().isEqualTo(409);
        assertThat(users.findById(first.id()).block().getEmail()).isEqualTo("first@example.com");
    }

    @Test
    void shouldValidateHttpFieldsPaginationAndMalformedRequests() {
        client.post().uri("/api/v1/users")
                .bodyValue(new CreateUserRequest("", "invalid", null))
                .exchange().expectStatus().isBadRequest().expectBody().jsonPath("$.fieldErrors").isNotEmpty();
        client.get().uri("/api/v1/users?size=51").exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.status").isEqualTo(400);
        client.get().uri("/api/v1/users?page=-1").exchange().expectStatus().isBadRequest();
        client.get().uri("/api/v1/users/not-a-number").exchange().expectStatus().isBadRequest();
        client.post().uri("/api/v1/users").contentType(MediaType.APPLICATION_JSON).bodyValue("{")
                .exchange().expectStatus().isBadRequest();
        client.post().uri("/internal/users/resolve").bodyValue(Map.of("ids", new Long[]{-1L}))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void shouldValidateEntityWithoutControllerAndEnforceDatabaseUniqueness() {
        StepVerifier.create(users.save(new User("", "not-an-email", null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ConstraintViolationException.class);
                    assertThat(((ConstraintViolationException) error).getConstraintViolations())
                            .extracting(violation -> violation.getPropertyPath().toString())
                            .containsExactlyInAnyOrder("name", "email", "role");
                }).verify();
        create("First", "first@example.com", UserRole.DEVELOPER);
        client.post().uri("/api/v1/users")
                .bodyValue(new CreateUserRequest("Duplicate", "FIRST@EXAMPLE.COM", UserRole.ADMIN))
                .exchange().expectStatus().isEqualTo(409);
        assertThat(users.count().block()).isEqualTo(1);
        StepVerifier.create(users.save(new User("Duplicate", "FIRST@example.com", UserRole.ADMIN)))
                .expectError(DataIntegrityViolationException.class).verify();
    }

    @Test
    void shouldPreventConcurrentUpdateFromResurrectingDeletedUser() {
        var created = create("Alice", "alice@example.com", UserRole.DEVELOPER);
        var deleted = users.findById(created.id()).block();
        var stale = users.findById(created.id()).block();
        deleted.delete();
        users.save(deleted).block();
        stale.update("Resurrected", "alice@example.com", UserRole.ADMIN);
        StepVerifier.create(users.save(stale)).expectError(OptimisticLockingFailureException.class).verify();
        assertThat(users.findById(created.id()).block().isDeleted()).isTrue();
    }

    @Test
    void shouldExposeRelativeOpenApiAndHideInternalContract() {
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.servers[0].url").isEqualTo("/")
                .jsonPath("$.paths['/api/v1/users']").exists()
                .jsonPath("$.paths['/internal/users/resolve']").doesNotExist();
    }

    private UserResponse create(String name, String email, UserRole role) {
        return client.post().uri("/api/v1/users").bodyValue(new CreateUserRequest(name, email, role))
                .exchange().expectStatus().isCreated().expectBody(UserResponse.class)
                .returnResult().getResponseBody();
    }
}
