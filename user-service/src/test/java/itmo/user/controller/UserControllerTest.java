package itmo.user.controller;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.common.web.GlobalExceptionHandler;
import itmo.user.dto.CreateUserRequest;
import itmo.user.dto.UpdateUserRequest;
import itmo.user.dto.UserResponse;
import itmo.user.entity.UserRole;
import itmo.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {
    @Mock
    private UserService service;
    private WebTestClient client;
    private UserResponse user;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(new UserController(service))
                .controllerAdvice(new GlobalExceptionHandler()).build();
        user = new UserResponse(1L, "Alice", "alice@example.com", UserRole.DEVELOPER,
                Instant.parse("2026-09-22T12:00:00Z"), Instant.parse("2026-09-22T12:00:00Z"));
    }

    @Test
    void shouldCreateUser() {
        when(service.create(any())).thenReturn(Mono.just(user));
        client.post().uri("http://localhost/api/v1/users").bodyValue(request()).exchange()
                .expectStatus().isCreated().expectHeader().valueEquals("Location", "http://localhost/api/v1/users/1")
                .expectBody().jsonPath("$.id").isEqualTo(1).jsonPath("$.email").isEqualTo("alice@example.com");
    }

    @Test
    void shouldGetUserById() {
        when(service.findById(1L)).thenReturn(Mono.just(user));
        client.get().uri("/api/v1/users/1").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo("Alice").jsonPath("$.role").isEqualTo("DEVELOPER");
    }

    @Test
    void shouldReturnPageAndTotalCountHeader() {
        var page = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id"));
        when(service.count()).thenReturn(Mono.just(1L));
        when(service.findAll(page)).thenReturn(Flux.just(user));
        client.get().uri("/api/v1/users").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Total-Count", "1").expectBody().jsonPath("$[0].id").isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyJsonArrayWithTotalCount() {
        when(service.count()).thenReturn(Mono.just(0L));
        when(service.findAll(any())).thenReturn(Flux.empty());
        client.get().uri("/api/v1/users").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Total-Count", "0")
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json("[]");
    }

    @Test
    void shouldReturnCountFailureBeforeReadingUsers() {
        when(service.count()).thenReturn(Mono.error(new ConflictException("Count failed")));
        client.get().uri("/api/v1/users").exchange().expectStatus().isEqualTo(409)
                .expectHeader().doesNotExist("X-Total-Count")
                .expectBody().jsonPath("$.message").isEqualTo("Count failed");
        verify(service, never()).findAll(any());
    }

    @Test
    void shouldOverrideNoContentStatusOnDeleteFailure() {
        when(service.delete(42L)).thenReturn(Mono.error(new ResourceNotFoundException("User", 42L)));
        client.delete().uri("/api/v1/users/42").exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.status").isEqualTo(404);
    }

    @Test
    void shouldUpdateUser() {
        when(service.update(any(), any())).thenReturn(Mono.just(user));
        client.put().uri("/api/v1/users/1").bodyValue(new UpdateUserRequest("Alice", "alice@example.com", UserRole.DEVELOPER))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.email").isEqualTo("alice@example.com");
    }

    @Test
    void shouldDeleteUser() {
        when(service.delete(1L)).thenReturn(Mono.empty());
        client.delete().uri("/api/v1/users/1").exchange().expectStatus().isNoContent().expectBody().isEmpty();
        verify(service).delete(1L);
    }

    @Test
    void shouldReturnValidationErrors() {
        client.post().uri("/api/v1/users").bodyValue(new CreateUserRequest(" ", "wrong-email", null, "Test-password-123"))
                .exchange().expectStatus().isBadRequest().expectBody()
                .jsonPath("$.message").isEqualTo("Request validation failed")
                .jsonPath("$.path").isEqualTo("/api/v1/users").jsonPath("$.fieldErrors.length()").isEqualTo(3);
    }

    @Test
    void shouldReturnNotFoundError() {
        when(service.findById(42L)).thenReturn(Mono.error(new ResourceNotFoundException("User", 42L)));
        client.get().uri("/api/v1/users/42").exchange().expectStatus().isNotFound().expectBody()
                .jsonPath("$.status").isEqualTo(404).jsonPath("$.message").isEqualTo("User with id '42' was not found");
    }

    @Test
    void shouldReturnConflictError() {
        when(service.create(any())).thenReturn(Mono.error(new ConflictException("User already exists")));
        client.post().uri("/api/v1/users").bodyValue(request()).exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.status").isEqualTo(409).jsonPath("$.message").isEqualTo("User already exists");
    }

    @Test
    void shouldReturnBadRequestForMalformedBody() {
        client.post().uri("/api/v1/users").contentType(MediaType.APPLICATION_JSON).bodyValue("{\"role\":\"UNKNOWN\"}")
                .exchange().expectStatus().isBadRequest().expectBody()
                .jsonPath("$.message").isEqualTo("Request body or parameter is malformed");
    }

    @Test
    void shouldReturnStructuredErrorForInvalidPathVariableType() {
        client.get().uri("/api/v1/users/not-a-number").exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.path").isEqualTo("/api/v1/users/not-a-number");
    }

    private CreateUserRequest request() {
        return new CreateUserRequest("Alice", "alice@example.com", UserRole.DEVELOPER, "Test-password-123");
    }
}
