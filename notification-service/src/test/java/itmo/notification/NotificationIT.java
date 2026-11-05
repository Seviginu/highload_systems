package itmo.notification;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;

import itmo.events.task.TaskAssignedEvent;
import itmo.notification.application.*;
import itmo.security.TestTokens;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationIT {
    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.2"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add(
                "spring.kafka.producer.key-serializer",
                () -> "org.apache.kafka.common.serialization.StringSerializer");
        r.add(
                "spring.kafka.producer.value-serializer",
                () -> "org.apache.kafka.common.serialization.StringSerializer");
    }

    @TestConfiguration
    static class Topics {
        @Bean
        NewTopic taskEvents() {
            return TopicBuilder.name(TaskAssignedEvent.TOPIC).partitions(3).replicas(1).build();
        }
    }

    @Autowired WebTestClient http;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationService service;
    @Autowired ObjectMapper mapper;
    @Autowired KafkaTemplate<String, String> kafka;

    @BeforeEach
    void clear() {
        jdbc.execute("TRUNCATE notifications,processed_events");
    }

    WebTestClient as(long id, String role) {
        return http.mutate().defaultHeader("Authorization", TestTokens.bearer(id, role)).build();
    }

    AssignmentReceived assignment(long recipient) {
        return new AssignmentReceived(UUID.randomUUID(), 10, "TEST-1", List.of(recipient));
    }

    @Test
    void shouldConsumeKafkaAndDeduplicateBeforeCommittingOffset() throws Exception {
        var event =
                new TaskAssignedEvent(
                        UUID.randomUUID(),
                        TaskAssignedEvent.TYPE,
                        1,
                        Instant.now(),
                        10L,
                        0L,
                        1L,
                        List.of(2L),
                        new TaskAssignedEvent.Payload("TEST-1", 1L));
        String value = mapper.writeValueAsString(event);
        kafka.send(TaskAssignedEvent.TOPIC, "10", value).get(10, TimeUnit.SECONDS);
        var sent =
                kafka.send(TaskAssignedEvent.TOPIC, "10", value)
                        .get(10, TimeUnit.SECONDS)
                        .getRecordMetadata();
        try (var admin =
                AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            await().atMost(Duration.ofSeconds(20))
                    .untilAsserted(
                            () -> {
                                var offsets =
                                        admin.listConsumerGroupOffsets(
                                                        "notifications-task-assigned-v1")
                                                .partitionsToOffsetAndMetadata()
                                                .get(5, TimeUnit.SECONDS);
                                var position =
                                        offsets.get(
                                                new TopicPartition(sent.topic(), sent.partition()));
                                assertThat(position).isNotNull();
                                assertThat(position.offset()).isGreaterThan(sent.offset());
                            });
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events", Long.class))
                .isEqualTo(1);
        as(2, "DEVELOPER")
                .get()
                .uri("/api/v1/notifications")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-Total-Count", "1")
                .expectBody()
                .jsonPath("$[0].message")
                .isEqualTo("Task 'TEST-1' has been assigned to you");
    }

    @Test
    void shouldScopePagesAndReadOperationsToJwtOwnerIncludingAdmin() {
        service.receive(assignment(2));
        service.receive(assignment(2));
        service.receive(assignment(3));
        as(2, "DEVELOPER")
                .get()
                .uri("/api/v1/notifications?size=1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-Total-Count", "2")
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(1);
        as(99, "ADMIN")
                .get()
                .uri("/api/v1/notifications")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json("[]");
        UUID id =
                jdbc.queryForObject(
                        "SELECT id FROM notifications WHERE recipient_id=2 LIMIT 1", UUID.class);
        as(3, "DEVELOPER")
                .patch()
                .uri("/api/v1/notifications/{id}/read", id)
                .exchange()
                .expectStatus()
                .isNotFound();
        as(99, "ADMIN")
                .patch()
                .uri("/api/v1/notifications/{id}/read", id)
                .exchange()
                .expectStatus()
                .isNotFound();
        as(2, "DEVELOPER")
                .patch()
                .uri("/api/v1/notifications/{id}/read", id)
                .exchange()
                .expectStatus()
                .isNoContent();
        var read =
                jdbc.queryForObject(
                        "SELECT read_at FROM notifications WHERE id=?",
                        java.sql.Timestamp.class,
                        id);
        as(2, "DEVELOPER")
                .patch()
                .uri("/api/v1/notifications/{id}/read", id)
                .exchange()
                .expectStatus()
                .isNoContent();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT read_at FROM notifications WHERE id=?",
                                java.sql.Timestamp.class,
                                id))
                .isEqualTo(read);
    }

    @Test
    void shouldRejectInvalidRequestsAndUnauthenticatedAccess() {
        http.get()
                .uri("/api/v1/notifications")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(401);
        for (String query : List.of("?size=51", "?size=0", "?page=-1")) {
            as(2, "DEVELOPER")
                    .get()
                    .uri("/api/v1/notifications" + query)
                    .exchange()
                    .expectStatus()
                    .isBadRequest();
        }
        as(2, "DEVELOPER")
                .patch()
                .uri("/api/v1/notifications/not-a-uuid/read")
                .exchange()
                .expectStatus()
                .isBadRequest();
        http.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                .isEqualTo("bearer");
    }

    @Test
    void shouldRollbackInboxMarkerTogetherWithFailedNotification() {
        var event = assignment(2);
        jdbc.execute(
                "ALTER TABLE notifications ADD CONSTRAINT test_reject_notification CHECK(false)");
        try {
            assertThatThrownBy(() -> service.receive(event))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events", Long.class))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class))
                    .isZero();
        } finally {
            jdbc.execute("ALTER TABLE notifications DROP CONSTRAINT test_reject_notification");
        }
        service.receive(event);
        service.receive(event);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class))
                .isEqualTo(1);
    }
}
