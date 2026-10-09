package itmo.integration.user;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import itmo.common.exception.ConflictException;
import itmo.common.exception.DependencyUnavailableException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.support.TestUser;
import itmo.support.UserApiStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "resilience4j.circuitbreaker.instances.userDirectory.slidingWindowSize=3",
        "resilience4j.circuitbreaker.instances.userDirectory.minimumNumberOfCalls=3",
        "resilience4j.circuitbreaker.instances.userDirectory.waitDurationInOpenState=2s"
})
class UserCircuitBreakerIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    @ServiceConnection
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);
    private static final UserApiStub USERS = new UserApiStub();

    @DynamicPropertySource
    static void clientProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.user-service.url", USERS::url);
        registry.add("spring.cloud.openfeign.client.config.user-service.readTimeout", () -> 200);
    }

    @Autowired private UserDirectory directory;
    @Autowired private CircuitBreakerRegistry registry;
    @Autowired private TestRestTemplate client;
    @Autowired private JdbcTemplate jdbc;
    private CircuitBreaker breaker;
    private Long leadId;

    @BeforeEach
    void setUp() {
        USERS.clear();
        breaker = registry.circuitBreaker("userDirectory");
        breaker.reset();
        leadId = USERS.create(new TestUser("TEAM_LEAD")).getId();
    }

    @Test
    void shouldOpenAfterFailuresSkipHttpAndRecoverThroughHalfOpen() {
        USERS.unavailable(true);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> directory.requireUsers(leadId))
                    .isInstanceOf(DependencyUnavailableException.class);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(USERS.requestCount()).isEqualTo(3);
        long projects = jdbc.queryForObject("SELECT count(*) FROM projects", Long.class);
        long members = jdbc.queryForObject("SELECT count(*) FROM project_members", Long.class);
        var rejected = client.postForEntity("/api/v1/projects", Map.of(
                "name", "Rejected", "code", "CB_REJECTED", "status", "ACTIVE", "teamLeadId", leadId), Map.class);
        assertThat(rejected.getStatusCode().value()).isEqualTo(503);
        assertThat(rejected.getBody()).containsEntry("message", "User service is unavailable");
        assertThat(USERS.requestCount()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM projects", Long.class)).isEqualTo(projects);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_members", Long.class)).isEqualTo(members);

        USERS.unavailable(false);
        // The next admitted request after the configured wait is the first probe.
        await().atMost(4, TimeUnit.SECONDS).ignoreException(DependencyUnavailableException.class).untilAsserted(() -> {
            directory.requireUsers(leadId);
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        });
        directory.requireUsers(leadId);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(USERS.requestCount()).isEqualTo(5);
    }

    @Test
    void shouldReopenWhenHalfOpenProbeFails() {
        USERS.unavailable(true);
        breaker.transitionToOpenState();
        await().atMost(4, TimeUnit.SECONDS).until(() -> {
            try { directory.requireUsers(leadId); } catch (DependencyUnavailableException ignored) { }
            return USERS.requestCount() == 1;
        });
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThatThrownBy(() -> directory.requireUsers(leadId)).isInstanceOf(DependencyUnavailableException.class);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void shouldCountReadTimeoutsAsFailures() {
        USERS.delay(500);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> directory.requireUsers(leadId)).isInstanceOf(DependencyUnavailableException.class);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(USERS.requestCount()).isEqualTo(3);
    }

    @Test
    void shouldKeepMissingUsersAndWrongRolesOutsideFailureStatistics() {
        Long developerId = USERS.create(new TestUser("DEVELOPER")).getId();
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> directory.requireUsers(-1L)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> directory.requireTeamLead(developerId)).isInstanceOf(ConflictException.class);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(USERS.requestCount()).isEqualTo(8);
    }
}
