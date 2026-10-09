package itmo.project.controller;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.common.web.GlobalExceptionHandler;
import itmo.project.dto.CreateProjectRequest;
import itmo.project.dto.ProjectResponse;
import itmo.project.dto.UpdateProjectRequest;
import itmo.project.entity.ProjectStatus;
import itmo.project.service.ProjectService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    @Mock
    private ProjectService projectService;

    private WebTestClient client;
    private ProjectResponse response;

    @BeforeEach
    void setUp() {
        ProjectController controller = new ProjectController(projectService, new BlockingOperations(Schedulers.boundedElastic()));
        client = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
        response = new ProjectResponse(
                1L,
                "Platform",
                "PLATFORM",
                "Main platform",
                ProjectStatus.ACTIVE,
                Instant.parse("2026-09-24T12:00:00Z"),
                Instant.parse("2026-09-24T12:00:00Z")
        );
    }

    @Test
    void shouldCreateProject() throws Exception {
        when(projectService.create(any(CreateProjectRequest.class))).thenReturn(response);

        client.post().uri("http://localhost/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(validRequest()).exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "http://localhost/api/v1/projects/1")
                .expectBody().jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.code").isEqualTo("PLATFORM");
    }

    @Test
    void shouldGetProjectById() throws Exception {
        when(projectService.findById(1L)).thenReturn(response);

        client.get().uri("/api/v1/projects/1").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo("Platform")
                .jsonPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    void shouldReturnPageAndTotalCountHeader() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id"));
        when(projectService.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(response), pageable, 1));

        client.get().uri("/api/v1/projects").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Total-Count", "1")
                .expectBody().jsonPath("$[0].id").isEqualTo(1);
    }

    @Test
    void shouldUpdateProject() throws Exception {
        when(projectService.update(any(Long.class), any(UpdateProjectRequest.class))).thenReturn(response);

        client.put().uri("http://localhost/api/v1/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(validRequest()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.code").isEqualTo("PLATFORM");
    }

    @Test
    void shouldDeleteProject() throws Exception {
        doNothing().when(projectService).delete(1L);

        client.delete().uri("/api/v1/projects/1").exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(projectService).delete(1L);
    }

    @Test
    void shouldReturnValidationErrors() throws Exception {
        client.post().uri("http://localhost/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("""
                                {
                                  "name": " ",
                                  "code": "1-invalid",
                                  "status": null
                                }
                                """).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Request validation failed")
                .jsonPath("$.path").isEqualTo("/api/v1/projects")
                .jsonPath("$.fieldErrors.length()").isEqualTo(4);
    }

    @Test
    void shouldReturnNotFoundError() throws Exception {
        when(projectService.findById(42L)).thenThrow(new ResourceNotFoundException("Project", 42L));

        client.get().uri("/api/v1/projects/42").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.message").isEqualTo("Project with id '42' was not found");
    }

    @Test
    void shouldReturnConflictError() throws Exception {
        when(projectService.create(any(CreateProjectRequest.class)))
                .thenThrow(new ConflictException("Project already exists"));

        client.post().uri("http://localhost/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(validRequest()).exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.message").isEqualTo("Project already exists");
    }

    @Test
    void shouldReturnBadRequestForMalformedStatus() throws Exception {
        client.post().uri("http://localhost/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("""
                                {
                                  "name": "Platform",
                                  "code": "PLATFORM",
                                  "status": "UNKNOWN"
                                }
                                """).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Request body is malformed");
    }

    private String validRequest() {
        return """
                {
                  "name": "Platform",
                  "code": "PLATFORM",
                  "description": "Main platform",
                  "status": "ACTIVE",
                  "teamLeadId": 1
                }
                """;
    }
}
