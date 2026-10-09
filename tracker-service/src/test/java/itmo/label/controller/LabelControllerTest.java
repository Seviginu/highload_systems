package itmo.label.controller;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.common.web.GlobalExceptionHandler;
import itmo.label.dto.CreateLabelRequest;
import itmo.label.dto.LabelResponse;
import itmo.label.dto.UpdateLabelRequest;
import itmo.label.service.LabelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import itmo.infrastructure.reactor.BlockingOperations;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabelControllerTest {

    @Mock
    private LabelService labelService;

    private WebTestClient client;
    private LabelResponse response;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(new LabelController(labelService, new BlockingOperations(Schedulers.boundedElastic())))
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
        response = new LabelResponse(
                1L,
                "Backend",
                "#A1B2C3",
                Instant.parse("2026-09-25T00:00:00Z")
        );
    }

    @Test
    void shouldCreateLabel() throws Exception {
        when(labelService.create(any(CreateLabelRequest.class))).thenReturn(response);

        client.post().uri("http://localhost/api/v1/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(validRequest()).exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "http://localhost/api/v1/labels/1")
                .expectBody().jsonPath("$.name").isEqualTo("Backend")
                .jsonPath("$.color").isEqualTo("#A1B2C3");
    }

    @Test
    void shouldGetLabelById() throws Exception {
        when(labelService.findById(1L)).thenReturn(response);

        client.get().uri("/api/v1/labels/1").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo(1);
    }

    @Test
    void shouldReturnPageAndTotalCount() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id"));
        when(labelService.findAll(pageable)).thenReturn(new PageImpl<>(List.of(response), pageable, 1));

        client.get().uri("/api/v1/labels").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Total-Count", "1")
                .expectBody().jsonPath("$[0].name").isEqualTo("Backend");
    }

    @Test
    void shouldUpdateLabel() throws Exception {
        when(labelService.update(any(Long.class), any(UpdateLabelRequest.class))).thenReturn(response);

        client.put().uri("http://localhost/api/v1/labels/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(validRequest()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo("Backend");
    }

    @Test
    void shouldDeleteLabel() throws Exception {
        client.delete().uri("/api/v1/labels/1").exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(labelService).delete(1L);
    }

    @Test
    void shouldReturnValidationErrors() throws Exception {
        client.post().uri("http://localhost/api/v1/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("""
                                {"name": " ", "color": "red"}
                                """).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Request validation failed")
                .jsonPath("$.fieldErrors.length()").isEqualTo(2);
    }

    @Test
    void shouldReturnNotFoundError() throws Exception {
        when(labelService.findById(42L)).thenThrow(new ResourceNotFoundException("Label", 42L));

        client.get().uri("/api/v1/labels/42").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.message").isEqualTo("Label with id '42' was not found");
    }

    @Test
    void shouldReturnConflictError() throws Exception {
        when(labelService.create(any(CreateLabelRequest.class)))
                .thenThrow(new ConflictException("Label already exists"));

        client.post().uri("http://localhost/api/v1/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(validRequest()).exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.message").isEqualTo("Label already exists");
    }

    private String validRequest() {
        return """
                {"name": "Backend", "color": "#A1B2C3"}
                """;
    }
}
