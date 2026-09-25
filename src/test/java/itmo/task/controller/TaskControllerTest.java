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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    @Mock
    private TaskService taskService;

    private MockMvc mockMvc;
    private TaskResponse response;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TaskController(taskService))
                .setControllerAdvice(new GlobalExceptionHandler())
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

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/tasks/1"))
                .andExpect(jsonPath("$.taskKey").value("PLATFORM-1"))
                .andExpect(jsonPath("$.labelIds[0]").value(5));
    }

    @Test
    void shouldGetTaskById() throws Exception {
        when(taskService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void shouldReturnPageAndTotalCount() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id"));
        TaskFilter filter = new TaskFilter(4L, 2L, 3L, 5L, TaskStatus.TODO, TaskPriority.HIGH);
        when(taskService.findAll(filter, pageable)).thenReturn(new PageImpl<>(List.of(response), pageable, 1));

        mockMvc.perform(get("/api/tasks")
                        .param("projectId", "4")
                        .param("authorId", "2")
                        .param("assigneeId", "3")
                        .param("labelId", "5")
                        .param("status", "TODO")
                        .param("priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].id").value(1));

        verify(taskService).findAll(filter, pageable);
    }

    @Test
    void shouldReturnCursorFeedAndNextCursor() throws Exception {
        when(taskService.findFeed(null, 20))
                .thenReturn(new TaskFeedResponse(List.of(response), 1L));

        mockMvc.perform(get("/api/tasks/feed"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Next-Cursor", "1"))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void shouldUpdateTask() throws Exception {
        when(taskService.update(any(Long.class), any(UpdateTaskRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskKey").value("PLATFORM-1"));
    }

    @Test
    void shouldMoveTask() throws Exception {
        when(taskService.move(any(Long.class), any(MoveTaskRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/tasks/1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": 4,
                                  "assigneeId": 3,
                                  "labelIds": [5],
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(4));
    }

    @Test
    void shouldValidateMoveRequest() throws Exception {
        mockMvc.perform(post("/api/tasks/1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": 0,
                                  "assigneeId": -1,
                                  "labelIds": null,
                                  "version": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void shouldDeleteTask() throws Exception {
        mockMvc.perform(delete("/api/tasks/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(taskService).delete(1L);
    }

    @Test
    void shouldReturnValidationErrors() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskKey": "bad",
                                  "title": " ",
                                  "status": null,
                                  "priority": null,
                                  "authorId": 0,
                                  "projectId": null,
                                  "labelIds": [0]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void shouldReturnNotFoundError() throws Exception {
        when(taskService.findById(42L)).thenThrow(new ResourceNotFoundException("Task", 42L));

        mockMvc.perform(get("/api/tasks/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task with id '42' was not found"));
    }

    @Test
    void shouldReturnConflictError() throws Exception {
        when(taskService.create(any(CreateTaskRequest.class)))
                .thenThrow(new ConflictException("Task already exists"));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Task already exists"));
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
