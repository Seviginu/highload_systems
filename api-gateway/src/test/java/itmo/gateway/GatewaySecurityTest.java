package itmo.gateway;

import com.sun.net.httpserver.HttpServer;
import itmo.security.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.location=classpath:gateway-test.yml")
class GatewaySecurityTest {
    private static final AtomicReference<String> AUTHORIZATION = new AtomicReference<>();
    private static final HttpServer DOWNSTREAM = downstream();
    @Autowired private WebTestClient client;

    @DynamicPropertySource
    static void route(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "test-api");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0]", () -> "Path=/api/v1/**");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].uri",
                () -> "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        DOWNSTREAM.stop(0);
    }

    @Test
    void shouldRequireJwtAndForwardOriginalAuthorization() {
        client.get().uri("/api/v1/tasks").exchange().expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.status").isEqualTo(401);
        client.get().uri("/api/v1/tasks").header("Authorization", "Bearer invalid")
                .exchange().expectStatus().isUnauthorized();
        String bearer = TestTokens.bearer();
        client.get().uri("/api/v1/users").header("Authorization", bearer).exchange().expectStatus().isOk();
        assertThat(AUTHORIZATION.get()).isEqualTo(bearer);
    }

    @Test
    void shouldRestrictUsersAndKeepLoginPublicWithoutOpeningInternalRoutes() {
        client.get().uri("/api/v1/users").header("Authorization", TestTokens.bearer(1, "DEVELOPER"))
                .exchange().expectStatus().isForbidden().expectBody().jsonPath("$.status").isEqualTo(403);
        client.post().uri("/api/v1/auth/login").exchange().expectStatus().isOk();
        client.post().uri("/internal/users/resolve").header("Authorization", TestTokens.bearer())
                .exchange().expectStatus().isForbidden();
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    private static HttpServer downstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/", exchange -> {
                AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
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
