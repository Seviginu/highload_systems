package itmo.user;

import itmo.user.dto.CreateUserRequest;
import itmo.user.dto.UpdateUserRequest;
import itmo.user.dto.UserResponse;
import itmo.user.entity.User;
import itmo.user.entity.UserRole;
import itmo.user.repository.UserRepository;
import itmo.user.service.UserService;
import itmo.infrastructure.security.AdminBootstrap;
import jakarta.validation.Validator;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private UserService service;
    @Autowired
    private Validator validator;

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
        assertThat(count.get("count")).isEqualTo(2L);
    }

    @Test
    void shouldPerformReactiveCrudAndReturnLocation() {
        var created = client.post().uri("/api/v1/users")
                .bodyValue(new CreateUserRequest(" Alice ", "alice@example.com", UserRole.DEVELOPER, "Test-password-123"))
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
                .bodyValue(new CreateUserRequest("Duplicate", "REMOVED@EXAMPLE.COM", UserRole.ADMIN, "Test-password-123"))
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
                .bodyValue(new CreateUserRequest("", "invalid", null, "Test-password-123"))
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
                .bodyValue(new CreateUserRequest("Duplicate", "FIRST@EXAMPLE.COM", UserRole.ADMIN, "Test-password-123"))
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

    @Test
    void shouldHashPasswordAndNeverReturnCredentials() {
        var created = create("Alice", "alice@example.com", UserRole.DEVELOPER);
        var stored = users.findById(created.id()).block();
        assertThat(stored.getPasswordHash()).isNotEqualTo("Test-password-123");
        assertThat(passwords.matches("Test-password-123", stored.getPasswordHash())).isTrue();
        client.get().uri("/api/v1/users/{id}", created.id()).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.password").doesNotExist().jsonPath("$.passwordHash").doesNotExist();
        client.get().uri("/api/v1/users").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].password").doesNotExist().jsonPath("$[0].passwordHash").doesNotExist();
        client.post().uri("/internal/users/resolve").bodyValue(Map.of("ids", new Long[]{created.id()}))
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$[0].password").doesNotExist().jsonPath("$[0].passwordHash").doesNotExist();
        client.put().uri("/api/v1/users/{id}", created.id())
                .bodyValue(new UpdateUserRequest("New name", "alice@example.com", UserRole.TEAM_LEAD))
                .exchange().expectStatus().isOk();
        assertThat(users.findById(created.id()).block().getPasswordHash()).isEqualTo(stored.getPasswordHash());
    }

    @Test
    void shouldValidatePasswordAndNotLeakItInErrors() {
        for (String password : new String[]{"", "short", " ".repeat(8), "я".repeat(37), "a".repeat(73)}) {
            client.post().uri("/api/v1/users")
                    .bodyValue(new CreateUserRequest("Alice", "alice@example.com", UserRole.DEVELOPER, password))
                    .exchange().expectStatus().isBadRequest().expectBody()
                    .jsonPath("$.fieldErrors[0].field").isEqualTo("password")
                    .jsonPath("$.fieldErrors[0].rejectedValue").doesNotExist();
        }
        client.post().uri("/api/v1/users").bodyValue(Map.of(
                        "name", "Alice", "email", "alice@example.com", "role", "DEVELOPER"))
                .exchange().expectStatus().isBadRequest();
        assertThat(users.count().block()).isZero();
    }

    @Test
    void shouldBootstrapAdminOnceWithoutResettingCredentials() {
        // An old ADMIN without credentials must not prevent initial setup.
        users.save(new User("Legacy", "legacy@example.com", UserRole.ADMIN)).block();
        var bootstrap = new AdminBootstrap(users, service, validator,
                "Supervisor", "supervisor@example.com", "Bootstrap-password-123");
        bootstrap.run(null);
        var admin = users.findAll().filter(u -> u.getEmail().equals("supervisor@example.com")).blockFirst();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(passwords.matches("Bootstrap-password-123", admin.getPasswordHash())).isTrue();
        new AdminBootstrap(users, service, validator,
                "Changed", "other@example.com", "Different-password-123").run(null);
        assertThat(users.count().block()).isEqualTo(2);
        var unchanged = users.findById(admin.getId()).block();
        assertThat(unchanged.getPasswordHash()).isEqualTo(admin.getPasswordHash());
        assertThat(unchanged.getName()).isEqualTo("Supervisor");
    }

    @Test
    void shouldNeverPromoteExistingBootstrapEmail() {
        var existing = users.save(new User("Existing", "supervisor@example.com", UserRole.DEVELOPER)).block();
        var bootstrap = new AdminBootstrap(users, service, validator,
                "Supervisor", "supervisor@example.com", "Bootstrap-password-123");
        StepVerifier.create(bootstrap.initialize()).expectError(itmo.common.exception.ConflictException.class).verify();
        var unchanged = users.findById(existing.getId()).block();
        assertThat(unchanged.getRole()).isEqualTo(UserRole.DEVELOPER);
        assertThat(unchanged.getPasswordHash()).isNull();
        assertThat(users.count().block()).isEqualTo(1);
    }

    @Test
    void shouldRejectInvalidBootstrapConfigurationWithoutExposingPassword() {
        var bootstrap = new AdminBootstrap(users, service, validator,
                "Supervisor", "invalid", "short");
        StepVerifier.create(bootstrap.initialize()).expectErrorSatisfies(error -> {
            assertThat(error).isInstanceOf(IllegalStateException.class);
            assertThat(error.getMessage()).isEqualTo("Invalid bootstrap admin configuration");
        }).verify();
        assertThat(users.count().block()).isZero();
    }

    private UserResponse create(String name, String email, UserRole role) {
        return client.post().uri("/api/v1/users").bodyValue(new CreateUserRequest(name, email, role, "Test-password-123"))
                .exchange().expectStatus().isCreated().expectBody(UserResponse.class)
                .returnResult().getResponseBody();
    }
}
