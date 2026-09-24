package itmo.task.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import itmo.common.web.ApiError;
import itmo.task.dto.CreateTaskRequest;
import itmo.task.dto.MoveTaskRequest;
import itmo.task.dto.TaskFeedResponse;
import itmo.task.dto.TaskResponse;
import itmo.task.dto.UpdateTaskRequest;
import itmo.task.service.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "Task management")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @Operation(summary = "Create a task")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Task created",
                    headers = @Header(
                            name = "Location",
                            description = "URI of the created task",
                            schema = @Schema(type = "string", format = "uri")
                    ),
                    content = @Content(schema = @Schema(implementation = TaskResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Related project, user or label not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Task key is already used",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        TaskResponse response = taskService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a task by id")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Task found",
                    content = @Content(schema = @Schema(implementation = TaskResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public TaskResponse findById(
            @Parameter(description = "Task identifier", example = "1")
            @PathVariable Long id
    ) {
        return taskService.findById(id);
    }

    @GetMapping
    @Operation(summary = "Get a page of tasks")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Page returned",
                    headers = @Header(
                            name = "X-Total-Count",
                            description = "Total number of tasks",
                            schema = @Schema(type = "integer", format = "int64")
                    ),
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TaskResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid page or size",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public ResponseEntity<List<TaskResponse>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        Page<TaskResponse> result = taskService.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"))
        );
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(result.getTotalElements()))
                .body(result.getContent());
    }

    @GetMapping("/feed")
    @Operation(summary = "Get a cursor-based task feed")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Task feed returned",
                    headers = @Header(
                            name = "X-Next-Cursor",
                            description = "Last returned task id when another page is available",
                            schema = @Schema(type = "integer", format = "int64")
                    ),
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TaskResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid cursor or limit",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public ResponseEntity<List<TaskResponse>> findFeed(
            @RequestParam(required = false) @Min(0) Long afterId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit
    ) {
        TaskFeedResponse result = taskService.findFeed(afterId, limit);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.nextCursor() != null) {
            response.header("X-Next-Cursor", String.valueOf(result.nextCursor()));
        }
        return response.body(result.tasks());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a task")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Task updated",
                    content = @Content(schema = @Schema(implementation = TaskResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Task or related resource not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Task key or version conflict",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public TaskResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request
    ) {
        return taskService.update(id, request);
    }

    @PostMapping("/{id}/move")
    @Operation(summary = "Move a task to another project")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Task moved",
                    content = @Content(schema = @Schema(implementation = TaskResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Task or related resource not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Assignee membership or version conflict",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public TaskResponse move(
            @PathVariable Long id,
            @Valid @RequestBody MoveTaskRequest request
    ) {
        return taskService.move(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a task")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task deleted"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
