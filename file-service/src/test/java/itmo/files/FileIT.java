package itmo.files;

import static org.assertj.core.api.Assertions.*;

import itmo.files.adapter.in.web.FileResponse;
import itmo.security.TestTokens;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.services.s3.S3Client;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import reactor.core.publisher.Flux;

import java.util.*;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FileIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> S3 = new GenericContainer<>(
            "pgsty/silo:RELEASE.2026-10-26T00-00-00Z")
            .withEnv("MINIO_ROOT_USER", "test-access")
            .withEnv("MINIO_ROOT_PASSWORD", "test-secret")
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait
                    .forHttp("/minio/health/ready").forPort(9000));

    @DynamicPropertySource
    static void s3Properties(DynamicPropertyRegistry registry) {
        registry.add("app.files.s3.endpoint", () -> "http://" + S3.getHost() + ":" + S3.getMappedPort(9000));
        registry.add("app.files.s3.access-key", () -> "test-access");
        registry.add("app.files.s3.secret-key", () -> "test-secret");
        registry.add("app.files.s3.bucket", () -> "files-test");
    }

    @Autowired S3Client s3;
    WebTestClient http;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        http = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        jdbc.execute("TRUNCATE file_contents,files,outbox_events");
        s3.listObjectsV2Paginator(r -> r.bucket("files-test")).contents()
                .forEach(object -> s3.deleteObject(r -> r.bucket("files-test").key(object.key())));
    }

    WebTestClient as(long id, String role) {
        return http.mutate().defaultHeader("Authorization", TestTokens.bearer(id, role)).build();
    }

    WebTestClient.RequestHeadersSpec<?> upload(WebTestClient client, byte[] bytes, String name) {
        var body = new MultipartBodyBuilder();
        body.part(
                        "file",
                        new ByteArrayResource(bytes) {
                            @Override
                            public String getFilename() {
                                return name;
                            }
                        })
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        return client.post()
                .uri("/api/v1/files")
                .body(BodyInserters.fromMultipartData(body.build()));
    }

    @Test
    void shouldStoreMetadataBytesAndEventAndDownloadOnlyForOwnerOrAdmin() throws Exception {
        byte[] bytes = {0, 1, 2, 127, -128, -1};
        var result =
                upload(as(2, "DEVELOPER"), bytes, "../пример.bin")
                        .exchange()
                        .expectStatus()
                        .isCreated()
                        .expectBody(FileResponse.class)
                        .returnResult();
        var file = result.getResponseBody();
        assertThat(file.ownerId()).isEqualTo(2);
        assertThat(file.name()).isEqualTo("пример.bin");
        assertThat(result.getResponseHeaders().getLocation().toString())
                .isEqualTo("/api/v1/files/" + file.id());
        assertThat(file.size()).isEqualTo(bytes.length);
        String uri = "/api/v1/files/" + file.id();
        as(2, "DEVELOPER")
                .get()
                .uri(uri)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.ownerId")
                .isEqualTo(2)
                .jsonPath("$.bytes")
                .doesNotExist();
        for (var client : List.of(as(2, "DEVELOPER"), as(99, "ADMIN"))) {
            client.get()
                    .uri(uri + "/content")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .expectHeader()
                    .valueEquals("Content-Length", Integer.toString(bytes.length))
                    .expectHeader()
                    .value(
                            "Content-Disposition",
                            v ->
                                    assertThat(v)
                                            .contains("attachment", "filename*=")
                                            .doesNotContain("../", "\r", "\n"))
                    .expectBody(byte[].class)
                    .isEqualTo(bytes);
        }
        for (String path : List.of(uri, uri + "/content")) {
            as(3, "TEAM_LEAD")
                    .get()
                    .uri(path)
                    .exchange()
                    .expectStatus()
                    .isForbidden()
                    .expectBody()
                    .jsonPath("$.status")
                    .isEqualTo(403);
            http.get().uri(path).exchange().expectStatus().isUnauthorized();
        }
        var event =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(
                                jdbc.queryForObject(
                                        "SELECT payload FROM outbox_events", String.class));
        assertThat(event.path("eventType").asText()).isEqualTo("FileUploaded");
        assertThat(event.path("aggregateId").asText()).isEqualTo(file.id().toString());
        assertThat(event.path("recipientIds").get(0).asLong()).isEqualTo(2);
        assertThat(event.path("payload").path("size").asLong()).isEqualTo(bytes.length);
        assertThat(event.toString()).doesNotContain("password", "Bearer", "bytes", "filename");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM file_contents", Long.class)).isZero();
        assertThat(s3.getObjectAsBytes(r -> r.bucket("files-test").key(file.id().toString()))
                .asByteArray()).isEqualTo(bytes);
    }

    @Test
    void shouldAllowEveryRoleAndRejectEmptyOversizedMissingOrMalformedInput() {
        for (String role : List.of("ADMIN", "TEAM_LEAD", "DEVELOPER"))
            upload(as(2, role), new byte[1024], "limit.bin").exchange().expectStatus().isCreated();
        upload(as(2, "DEVELOPER"), new byte[0], "empty.bin")
                .exchange()
                .expectStatus()
                .isBadRequest();
        upload(as(2, "DEVELOPER"), new byte[1025], "large.bin")
                .exchange()
                .expectStatus()
                .isEqualTo(413)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(413);
        upload(http, new byte[1], "unauthorized.bin").exchange().expectStatus().isUnauthorized();
        as(2, "DEVELOPER")
                .get()
                .uri("/api/v1/files/" + UUID.randomUUID())
                .exchange()
                .expectStatus()
                .isNotFound();
        as(2, "DEVELOPER")
                .get()
                .uri("/api/v1/files/not-a-uuid/content")
                .exchange()
                .expectStatus()
                .isBadRequest();
        var body = new MultipartBodyBuilder();
        body.part("wrong", "value");
        as(2, "DEVELOPER")
                .post()
                .uri("/api/v1/files")
                .body(BodyInserters.fromMultipartData(body.build()))
                .exchange()
                .expectStatus()
                .isBadRequest();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM files", Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class))
                .isEqualTo(3);
    }

    @Test
    void shouldRejectChunkedRequestWithoutContentLengthBeforeSaving() {
        byte[] bytes = new byte[70000];
        as(2, "DEVELOPER")
                .post()
                .uri("/api/v1/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(
                        BodyInserters.fromDataBuffers(
                                Flux.<org.springframework.core.io.buffer.DataBuffer>just(
                                        DefaultDataBufferFactory.sharedInstance.wrap(bytes))))
                .exchange()
                .expectStatus()
                .isEqualTo(413)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(413);
        as(2, "DEVELOPER")
                .post()
                .uri("/api/v1/files")
                .header("Content-Type", "multipart/form-data; boundary=missing")
                .bodyValue("invalid multipart")
                .exchange()
                .expectStatus()
                .isBadRequest();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM files", Long.class)).isZero();
    }

    @Test
    void shouldRollbackMetadataAndBytesWhenOutboxFails() {
        jdbc.execute("ALTER TABLE outbox_events ADD CONSTRAINT test_failure CHECK(false)");
        try {
            upload(as(2, "DEVELOPER"), new byte[] {1}, "file.bin")
                    .exchange()
                    .expectStatus()
                    .is5xxServerError();
            for (String table : List.of("files", "file_contents", "outbox_events"))
                assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class))
                        .isZero();
            assertThat(s3.listObjectsV2(r -> r.bucket("files-test")).contents()).isEmpty();
        } finally {
            jdbc.execute("ALTER TABLE outbox_events DROP CONSTRAINT test_failure");
        }
    }

    @Test
    void shouldParseMultipartAcrossRealNetworkBuffers() {
        String boundary = "file-proof";
        byte[] bytes =
                ("--"
                                + boundary
                                + "\r\n"
                                + "Content-Disposition: form-data; name=\"file\";"
                                + " filename=\"proof.bin\"\r\n"
                                + "Content-Type: application/octet-stream\r\n\r\n"
                                + "abc\r\n"
                                + "--"
                                + boundary
                                + "--\r\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var buffer = DefaultDataBufferFactory.sharedInstance;
        var chunks =
                Flux.<org.springframework.core.io.buffer.DataBuffer>just(
                        buffer.wrap(Arrays.copyOfRange(bytes, 0, 17)),
                        buffer.wrap(Arrays.copyOfRange(bytes, 17, bytes.length)));
        as(2, "DEVELOPER")
                .post()
                .uri("/api/v1/files")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .body(BodyInserters.fromDataBuffers(chunks))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.size")
                .isEqualTo(3);
    }

    @Test
    void shouldExposeSecuredOpenApi() {
        http.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/files'].post.responses['201']")
                .exists()
                .jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                .isEqualTo("bearer")
                .jsonPath(
                        "$.paths['/api/v1/files'].post.requestBody.content['multipart/form-data']")
                .exists();
    }
}
