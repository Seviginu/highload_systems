package itmo.infrastructure.reactor;

import itmo.project.entity.ProjectStatus;
import itmo.support.TestUser;
import itmo.support.UserApiStub;
import itmo.task.dto.CreateTaskRequest;
import itmo.task.dto.TaskResponse;
import itmo.task.entity.TaskPriority;
import itmo.task.entity.TaskStatus;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.scheduler.Schedulers;
import reactor.netty.resources.LoopResources;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.blocking.threads=2", "app.blocking.queued-tasks=10"})
@Import(BlockingBoundaryIT.ProbeConfiguration.class)
class BlockingBoundaryIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    @ServiceConnection
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);
    private static final UserApiStub USERS = new UserApiStub();

    @DynamicPropertySource
    static void userClientProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.user-service.url", USERS::url);
        registry.add("spring.cloud.openfeign.client.config.user-service.readTimeout", () -> 10000);
    }

    @Autowired private TestRestTemplate client;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationContext context;
    @Autowired private BoundaryProbe probe;

    @BeforeEach
    void setUp() {
        probe.capture = false;
        probe.threads.clear();
        USERS.clear();
        jdbc.update("DELETE FROM task_labels");
        jdbc.update("DELETE FROM tasks");
        jdbc.update("DELETE FROM project_members");
        jdbc.update("DELETE FROM projects");
    }

    @Test
    void shouldKeepSingleHttpEventLoopResponsiveDuringFeignAndKeepJpaAndRedisOnWorkers() throws Exception {
        assertThat(context).isInstanceOf(ReactiveWebServerApplicationContext.class);
        Long userId = USERS.create(new TestUser("TEAM_LEAD")).getId();
        Long projectId = jdbc.queryForObject("""
                INSERT INTO projects (name, code, status) VALUES ('Boundary', 'BOUNDARY', ?) RETURNING id
                """, Long.class, ProjectStatus.ACTIVE.name());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        USERS.blockResponses(entered, release);
        probe.capture = true;
        var request = new CreateTaskRequest("BOUNDARY-1", "Task", null, TaskStatus.TODO, TaskPriority.MEDIUM,
                userId, null, projectId, Set.of());
        var writing = CompletableFuture.supplyAsync(() -> client.postForEntity("/api/v1/tasks", request, TaskResponse.class));
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).as("Feign request reached the blocked HTTP stub").isTrue();
            assertThat(writing).isNotDone();
            var info = CompletableFuture.supplyAsync(() -> client.getForEntity("/actuator/info", String.class))
                    .get(3, TimeUnit.SECONDS);
            assertThat(info.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(writing).as("HTTP info completed before the blocked Feign call was released").isNotDone();
        } finally {
            release.countDown();
        }
        var created = writing.get(5, TimeUnit.SECONDS);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        var read = client.getForEntity("/api/v1/tasks/{id}", TaskResponse.class, created.getBody().id());
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody()).isEqualTo(created.getBody());
        assertThat(probe.threads).containsKeys("feign", "jpa", "redis");
        probe.threads.forEach((kind, names) -> assertThat(names).as(kind + " worker threads")
                .allMatch(name -> name.startsWith("tracker-blocking-")));
        probe.capture = false;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean(destroyMethod = "dispose")
        LoopResources boundaryHttpLoops() {
            return LoopResources.create("boundary-http", 1, true);
        }
        @Bean
        NettyServerCustomizer boundaryHttpServer(LoopResources boundaryHttpLoops) {
            return server -> server.runOn(boundaryHttpLoops);
        }
        @Bean
        BoundaryProbe boundaryProbe() { return new BoundaryProbe(); }
    }

    @Aspect
    static class BoundaryProbe {
        private volatile boolean capture;
        private final Map<String, Set<String>> threads = new ConcurrentHashMap<>();

        @Before("bean(taskRepository)")
        public void beforeJpa() {
            if (capture) {
                record("jpa");
                assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                        .as("JPA runs inside its synchronous transaction").isTrue();
            }
        }
        @Before("target(itmo.integration.user.UserClient)")
        public void beforeFeign() {
            if (capture) {
                record("feign");
                assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                        .as("Remote check precedes the local transaction").isFalse();
            }
        }
        @Before("target(org.springframework.data.redis.connection.RedisConnectionFactory) && execution(* *.getConnection(..))")
        public void beforeRedis() {
            if (capture) { record("redis"); }
        }
        private void record(String kind) {
            assertThat(Schedulers.isInNonBlockingThread()).as(kind + " must leave the HTTP event loop").isFalse();
            threads.computeIfAbsent(kind, key -> ConcurrentHashMap.newKeySet()).add(Thread.currentThread().getName());
        }
    }
}
