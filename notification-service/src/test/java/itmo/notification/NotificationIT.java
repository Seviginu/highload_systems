package itmo.notification;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import itmo.events.project.ProjectMemberAddedEvent;
import itmo.events.task.TaskAssignedEvent;
import itmo.events.task.TaskStatusChangedEvent;
import itmo.notification.application.*;
import itmo.security.TestTokens;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;

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
        r.add("app.notifications.retry-delay", () -> 100);
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
        NewTopic projectEvents() {
            return TopicBuilder.name(ProjectMemberAddedEvent.TOPIC)
                    .partitions(3)
                    .replicas(1)
                    .build();
        }

        @Bean
        NewTopic taskDlt() {
            return TopicBuilder.name(TaskAssignedEvent.TOPIC + ".dlt")
                    .partitions(3)
                    .replicas(1)
                    .build();
        }

        @Bean
        NewTopic projectDlt() {
            return TopicBuilder.name(ProjectMemberAddedEvent.TOPIC + ".dlt")
                    .partitions(3)
                    .replicas(1)
                    .build();
        }

        @Bean
        NewTopic taskEvents() {
            return TopicBuilder.name(TaskAssignedEvent.TOPIC).partitions(3).replicas(1).build();
        }
    }

    @Autowired WebTestClient http;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationService service;
    @Autowired ObjectMapper mapper;
    @MockitoSpyBean(name = "kafkaTemplate") KafkaTemplate<?, ?> kafkaSpy;
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

    @Test
    void shouldConsumeAndDeduplicateBothNewTypes() throws Exception {
        var events =
                List.of(
                        new TaskStatusChangedEvent(
                                UUID.randomUUID(),
                                TaskStatusChangedEvent.TYPE,
                                1,
                                Instant.now(),
                                10L,
                                1L,
                                1L,
                                List.of(2L),
                                new TaskStatusChangedEvent.Payload("TEST-1", "TODO", "DONE")),
                        new ProjectMemberAddedEvent(
                                UUID.randomUUID(),
                                ProjectMemberAddedEvent.TYPE,
                                1,
                                Instant.now(),
                                20L,
                                null,
                                1L,
                                List.of(2L),
                                new ProjectMemberAddedEvent.Payload(3L, "Platform")));
        for (Object event : events) {
            String topic =
                    event instanceof TaskStatusChangedEvent
                            ? TaskAssignedEvent.TOPIC
                            : ProjectMemberAddedEvent.TOPIC;
            for (int n = 0; n < 2; n++)
                kafka.send(topic, "10", mapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);
        }
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.queryForObject(
                                                        "SELECT count(*) FROM processed_events",
                                                        Long.class))
                                        .isEqualTo(2L));
        // Wait for duplicates to be committed too, before the next test clears the inbox.
        awaitOffsets();
        assertThat(jdbc.queryForList("SELECT type FROM notifications ORDER BY type", String.class))
                .containsExactly("ProjectMemberAdded", "TaskStatusChanged");
        assertThat(jdbc.queryForList("SELECT resource_type FROM notifications", String.class))
                .containsExactlyInAnyOrder("Task", "Project");
    }

    void awaitOffsets() throws Exception {
        try (var admin =
                AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            var ends =
                    admin.listOffsets(
                                    java.util.stream.Stream.of(
                                                    TaskAssignedEvent.TOPIC,
                                                    ProjectMemberAddedEvent.TOPIC)
                                            .flatMap(
                                                    t ->
                                                            java.util.stream.IntStream.range(0, 3)
                                                                    .mapToObj(
                                                                            p ->
                                                                                    new TopicPartition(
                                                                                            t, p)))
                                            .collect(
                                                    java.util.stream.Collectors.toMap(
                                                            p -> p,
                                                            p ->
                                                                    org.apache.kafka.clients.admin
                                                                            .OffsetSpec.latest())))
                            .all()
                            .get(5, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(20))
                    .untilAsserted(
                            () -> {
                                var offsets =
                                        admin.listConsumerGroupOffsets(
                                                        "notifications-task-assigned-v1")
                                                .partitionsToOffsetAndMetadata()
                                                .get(5, TimeUnit.SECONDS);
                                ends.forEach(
                                        (p, e) -> {
                                            if (e.offset() > 0) {
                                                assertThat(offsets.get(p)).isNotNull();
                                                assertThat(offsets.get(p).offset())
                                                        .isGreaterThanOrEqualTo(e.offset());
                                            }
                                        });
                            });
        }
    }

    @Test
    void shouldNotCommitPoisonRecordUntilDltPublicationSucceeds() throws Exception {
        var reject = new AtomicBoolean(true);
        var failures = new AtomicInteger();
        doAnswer(
                        call -> {
                            ProducerRecord<String, String> record = call.getArgument(0);
                            if (record.topic().endsWith(".dlt") && reject.get()) {
                                failures.incrementAndGet();
                                return CompletableFuture.failedFuture(
                                        new IllegalStateException("Simulated unavailable DLT"));
                            }
                            return call.callRealMethod();
                        })
                .when(kafka)
                .send(org.mockito.ArgumentMatchers.<ProducerRecord<String, String>>any());
        String poison = "{\"eventType\":\"Unsupported\",\"marker\":\"" + UUID.randomUUID() + "\"}";
        var metadata =
                kafka.send(TaskAssignedEvent.TOPIC, "poison", poison)
                        .get(10, TimeUnit.SECONDS)
                        .getRecordMetadata();
        try {
            await().atMost(Duration.ofSeconds(20)).until(() -> failures.get() > 0);
            try (var admin =
                    AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
                var offsets =
                        admin.listConsumerGroupOffsets("notifications-task-assigned-v1")
                                .partitionsToOffsetAndMetadata()
                                .get(5, TimeUnit.SECONDS);
                var offset =
                        offsets.get(new TopicPartition(metadata.topic(), metadata.partition()));
                assertThat(offset == null || offset.offset() <= metadata.offset()).isTrue();
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events", Long.class))
                    .isZero();
        } finally {
            reject.set(false);
        }
        var props = new HashMap<String, Object>();
        props.put("bootstrap.servers", KAFKA.getBootstrapServers());
        props.put("group.id", UUID.randomUUID().toString());
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("enable.auto.commit", false);
        try (var consumer = new KafkaConsumer<String, String>(props)) {
            var partition = new TopicPartition(metadata.topic() + ".dlt", metadata.partition());
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            var recovered =
                    new java.util.ArrayList<
                            org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>();
            await().atMost(Duration.ofSeconds(20))
                    .until(
                            () -> {
                                consumer.poll(Duration.ofMillis(200))
                                        .forEach(
                                                r -> {
                                                    if (poison.equals(r.value())) recovered.add(r);
                                                });
                                return !recovered.isEmpty();
                            });
            var record = recovered.getFirst();
            assertThat(record.key()).isEqualTo("poison");
            assertThat(
                            record.headers()
                                    .lastHeader(
                                            org.springframework.kafka.support.KafkaHeaders
                                                    .DLT_ORIGINAL_TOPIC))
                    .isNotNull();
            assertThat(
                            record.headers()
                                    .lastHeader(
                                            org.springframework.kafka.support.KafkaHeaders
                                                    .DLT_EXCEPTION_MESSAGE))
                    .isNull();
        }
        awaitOffsets();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isZero();
    }
}
