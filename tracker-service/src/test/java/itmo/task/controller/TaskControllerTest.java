package itmo.task.controller;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.common.web.GlobalExceptionHandler;
import itmo.task.dto.CreateTaskRequest;
import itmo.task.dto.MoveTaskRequest;
import itmo.task.dto.TaskFeedResponse;
import itmo.task.dto.TaskFilter;
import itmo.task.dto.TaskResponse;
import itmo.task.dto.UpdateTaskRequest;
import itmo.task.entity.TaskPriority;
import itmo.task.entity.TaskStatus;
import itmo.task.service.TaskService;
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
class TaskControllerTest {

    @Mock
    private TaskService taskService;

    private WebTestClient client;
    private TaskResponse response;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(new TaskController(taskService, new BlockingOperations(Schedulers.boundedElastic())))
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
        response = new TaskResponse(
                1L,
                "PLATFORM-1",
                "Task",
                "Description",
                TaskStatus.TODO,
                TaskPriority.HIGH,
                2L,
                3L,
                4L,
                List.of(5L),
                0L,
                Instant.parse("2026-09-25T00:00:00Z"),
                Instant.parse("2026-09-25T00:00:00Z")
        );
    }

    @Test
    void shouldCreateTask() throws Exception {
        when(taskService.create(any(CreateTaskRequest.class))).thenReturn(response);

        client.post().uri("http://localhost/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(validCreateRequest()).exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "http://localhost/api/v1/tasks/1")
                .expectBody().jsonPath("$.taskKey").isEqualTo("PLATFORM-1")
                .jsonPath("$.labelIds[0]").isEqualTo(5);
    }

    @Test
    void shouldGetTaskById() throws Exception {
        when(taskService.findById(1L)).thenReturn(response);

        client.get().uri("/api/v1/tasks/1").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.version").isEqualTo(0);
    }

    @Test
    void shouldReturnPageAndTotalCount() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id"));
        TaskFilter filter = new TaskFilter(4L, 2L, 3L, 5L, TaskStatus.TODO, TaskPriority.HIGH);
        when(taskService.findAll(filter, pageable)).thenReturn(new PageImpl<>(List.of(response), pageable, 1));

        client.get().uri("/api/v1/tasks?projectId=4&authorId=2&assigneeId=3&labelId=5&status=TODO&priority=HIGH").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Total-Count", "1")
                .expectBody().jsonPath("$[0].id").isEqualTo(1);

        verify(taskService).findAll(filter, pageable);
    }

    @Test
    void shouldReturnCursorFeedAndNextCursor() throws Exception {
        when(taskService.findFeed(null, 20))
                .thenReturn(new TaskFeedResponse(List.of(response), 1L));

        client.get().uri("/api/v1/tasks/feed").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Next-Cursor", "1")
                .expectBody().jsonPath("$[0].id").isEqualTo(1);
    }

    @Test
    void shouldUpdateTask() throws Exception {
        when(taskService.update(any(Long.class), any(UpdateTaskRequest.class))).thenReturn(response);

        client.put().uri("http://localhost/api/v1/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(validUpdateRequest()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.taskKey").isEqualTo("PLATFORM-1");
    }

    @Test
    void shouldMoveTask() throws Exception {
        when(taskService.move(any(Long.class), any(MoveTaskRequest.class))).thenReturn(response);

        client.post().uri("http://localhost/api/v1/tasks/1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("""
                                {
                                  "projectId": 4,
                                  "assigneeId": 3,
                                  "labelIds": [5],
                                  "version": 0
                                }
                                """).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.projectId").isEqualTo(4);
    }

    @Test
    void shouldValidateMoveRequest() throws Exception {
        client.post().uri("http://localhost/api/v1/tasks/1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("""
                                {
                                  "projectId": 0,
                                  "assigneeId": -1,
                                  "labelIds": null,
                                  "version": -1
                                }
                                """).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.fieldErrors").isNotEmpty();
    }

    @Test
    void shouldDeleteTask() throws Exception {
        client.delete().uri("/api/v1/tasks/1").exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(taskService).delete(1L);
    }

    @Test
    void shouldReturnValidationErrors() throws Exception {
        client.post().uri("http://localhost/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("""
                                {
                                  "taskKey": "bad",
                                  "title": " ",
                                  "status": null,
                                  "priority": null,
                                  "authorId": 0,
                                  "projectId": null,
                                  "labelIds": [0]
                                }
                                """).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Request validation failed")
                .jsonPath("$.fieldErrors").isNotEmpty();
    }

    @Test
    void shouldReturnNotFoundError() throws Exception {
        when(taskService.findById(42L)).thenThrow(new ResourceNotFoundException("Task", 42L));

        client.get().uri("/api/v1/tasks/42").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.message").isEqualTo("Task with id '42' was not found");
    }

    @Test
    void shouldReturnConflictError() throws Exception {
        when(taskService.create(any(CreateTaskRequest.class)))
                .thenThrow(new ConflictException("Task already exists"));

        client.post().uri("http://localhost/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(validCreateRequest()).exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.message").isEqualTo("Task already exists");
    }

    @Test
    void shouldReturnStructured503ForRejectedBlockingWork() {
        when(taskService.findById(1L)).thenThrow(new java.util.concurrent.RejectedExecutionException("Full"));
        client.get().uri("/api/v1/tasks/1").exchange().expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.message").isEqualTo("Tracker is busy; retry later")
                .jsonPath("$.path").isEqualTo("/api/v1/tasks/1");
    }

    private String validCreateRequest() {
        return """
                {
                  "taskKey": "PLATFORM-1",
                  "title": "Task",
                  "description": "Description",
                  "status": "TODO",
                  "priority": "HIGH",
                  "authorId": 2,
                  "assigneeId": 3,
                  "projectId": 4,
                  "labelIds": [5]
                }
                """;
    }

    private String validUpdateRequest() {
        return """
                {
                  "taskKey": "PLATFORM-1",
                  "title": "Task",
                  "description": "Description",
                  "status": "TODO",
                  "priority": "HIGH",
                  "authorId": 2,
                  "assigneeId": 3,
                  "projectId": 4,
                  "labelIds": [5],
                  "version": 0
                }
                """;
    }
}
