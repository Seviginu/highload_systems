package itmo.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;

import itmo.security.TestTokens;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.config.location=classpath:gateway-test.yml",
            "app.files.max-file-bytes=1024"
        })
class GatewaySecurityTest {
    private static final AtomicReference<String> AUTHORIZATION = new AtomicReference<>();
    private static final HttpServer DOWNSTREAM = downstream();
    private WebTestClient client;
    @org.springframework.boot.test.web.server.LocalServerPort int port;

    @org.junit.jupiter.api.BeforeEach
    void connect() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private static final AtomicReference<byte[]> BODY = new AtomicReference<>();

    @DynamicPropertySource
    static void route(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "test-api");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].predicates[0]",
                () -> "Path=/api/v1/**");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].uri",
                () -> "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        DOWNSTREAM.stop(0);
    }

    @Test
    void shouldRequireJwtAndForwardOriginalAuthorization() {
        client.get()
                .uri("/api/v1/tasks")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(401);
        client.get()
                .uri("/api/v1/tasks")
                .header("Authorization", "Bearer invalid")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        String bearer = TestTokens.bearer();
        client.get()
                .uri("/api/v1/users")
                .header("Authorization", bearer)
                .exchange()
                .expectStatus()
                .isOk();
        assertThat(AUTHORIZATION.get()).isEqualTo(bearer);
    }

    @Test
    void shouldRestrictUsersAndKeepLoginPublicWithoutOpeningInternalRoutes() {
        client.get()
                .uri("/api/v1/users")
                .header("Authorization", TestTokens.bearer(1, "DEVELOPER"))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(403);
        client.post().uri("/api/v1/auth/login").exchange().expectStatus().isOk();
        client.post()
                .uri("/internal/users/resolve")
                .header("Authorization", TestTokens.bearer())
                .exchange()
                .expectStatus()
                .isForbidden();
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void shouldBoundUploadBodyWithAndWithoutContentLength() {
        var buffer = org.springframework.core.io.buffer.DefaultDataBufferFactory.sharedInstance;
        var body =
                reactor.core.publisher.Flux.<org.springframework.core.io.buffer.DataBuffer>just(
                        buffer.wrap(new byte[70000]));
        client.post()
                .uri("/api/v1/files")
                .header("Authorization", TestTokens.bearer())
                .body(org.springframework.web.reactive.function.BodyInserters.fromDataBuffers(body))
                .exchange()
                .expectStatus()
                .isEqualTo(413)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(413);
        client.post()
                .uri("/api/v1/files")
                .header("Authorization", TestTokens.bearer())
                .bodyValue(new byte[70000])
                .exchange()
                .expectStatus()
                .isEqualTo(413);
        client.post()
                .uri("/api/v1/files")
                .header("Authorization", TestTokens.bearer())
                .bodyValue(new byte[1024])
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void shouldForwardCompleteMultipartBodyAcrossNetworkBuffers() {
        byte[] body =
                ("--proof\r\n"
                     + "Content-Disposition: form-data; name=\"file\";"
                     + " filename=\"sample.bin\"\r\n\r\n"
                     + "abc\r\n"
                     + "--proof--\r\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var buffer = org.springframework.core.io.buffer.DefaultDataBufferFactory.sharedInstance;
        var chunks =
                reactor.core.publisher.Flux.<org.springframework.core.io.buffer.DataBuffer>just(
                        buffer.wrap(java.util.Arrays.copyOfRange(body, 0, 12)),
                        buffer.wrap(java.util.Arrays.copyOfRange(body, 12, body.length)));
        client.post()
                .uri("/api/v1/files")
                .header("Authorization", TestTokens.bearer())
                .header("Content-Type", "multipart/form-data; boundary=proof")
                .body(
                        org.springframework.web.reactive.function.BodyInserters.fromDataBuffers(
                                chunks))
                .exchange()
                .expectStatus()
                .isOk();
        assertThat(BODY.get()).isEqualTo(body);
    }

    private static HttpServer downstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/api/v1/",
                    exchange -> {
                        AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
                        BODY.set(exchange.getRequestBody().readAllBytes());
                        byte[] body = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, body.length);
                        exchange.getResponseBody().write(body);
                        exchange.close();
                    });
            server.start();
            return server;
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }
}
