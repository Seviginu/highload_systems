package itmo.tracker.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import itmo.tracker.application.model.PageQuery;
import itmo.tracker.application.model.ResultPage;
import itmo.tracker.application.model.TaskFilter;
import itmo.tracker.application.port.in.TaskService;
import itmo.tracker.domain.TaskPriority;
import itmo.tracker.domain.TaskStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

@Validated
@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks", description = "Task management")
public class TaskController {
    @PatchMapping("/{id}/status")
    @Operation(
            summary =
                    "Change task status: supervisor, project team lead or assigned active"
                            + " developer")
    @ApiResponse(responseCode = "403", description = "Current user cannot change this task")
    @ApiResponse(responseCode = "409", description = "Task version has changed")
    public Mono<TaskResponse> changeStatus(
            @PathVariable Long id, @Valid @RequestBody ChangeTaskStatusRequest request) {
        return CurrentUser.current()
                .flatMap(
                        actor ->
                                blocking.call(
                                        () ->
                                                WebMapper.toResponse(
                                                        taskService.changeStatus(
                                                                id,
                                                                WebMapper.toCommand(request),
                                                                actor))));
    }

    private final TaskService taskService;

    private final BlockingOperations blocking;

    public TaskController(TaskService taskService, BlockingOperations blocking) {
        this.taskService = taskService;
        this.blocking = blocking;
    }

    @PostMapping
    @Operation(summary = "Create a task")
    @ApiResponse(
            responseCode = "201",
            description = "Task created",
            headers =
                    @Header(
                            name = "Location",
                            description = "URI of the created task",
                            schema = @Schema(type = "string", format = "uri")),
            content = @Content(schema = @Schema(implementation = TaskResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Request validation failed",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Related project, user or label not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Task key is already used",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<TaskResponse> create(
            @Valid @RequestBody CreateTaskRequest request, ServerWebExchange exchange) {
        return CurrentUser.current()
                .flatMap(
                        actor ->
                                blocking.call(
                                        () -> {
                                            TaskResponse response =
                                                    WebMapper.toResponse(
                                                            taskService.create(
                                                                    WebMapper.toCommand(request),
                                                                    actor));
                                            URI location =
                                                    UriComponentsBuilder.fromUri(
                                                                    exchange.getRequest().getURI())
                                                            .path("/{id}")
                                                            .buildAndExpand(response.id())
                                                            .toUri();
                                            exchange.getResponse()
                                                    .getHeaders()
                                                    .setLocation(location);
                                            return response;
                                        }));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a task by id")
    @ApiResponse(
            responseCode = "200",
            description = "Task found",
            content = @Content(schema = @Schema(implementation = TaskResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Task not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Mono<TaskResponse> findById(
            @Parameter(description = "Task identifier", example = "1") @PathVariable Long id) {
        return blocking.call(() -> WebMapper.toResponse(taskService.findById(id)));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a page of tasks")
    @ApiResponse(
            responseCode = "200",
            description = "Page returned",
            headers =
                    @Header(
                            name = "X-Total-Count",
                            description = "Total number of tasks",
                            schema = @Schema(type = "integer", format = "int64")),
            content =
                    @Content(
                            array =
                                    @ArraySchema(
                                            schema = @Schema(implementation = TaskResponse.class))))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid filter, page or size",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Flux<TaskResponse> findAll(
            @Parameter(description = "Filter by project identifier", example = "1")
                    @RequestParam(required = false)
                    @Positive
                    Long projectId,
            @Parameter(description = "Filter by author identifier", example = "2")
                    @RequestParam(required = false)
                    @Positive
                    Long authorId,
            @Parameter(description = "Filter by assignee identifier", example = "3")
                    @RequestParam(required = false)
                    @Positive
                    Long assigneeId,
            @Parameter(description = "Filter by label identifier", example = "4")
                    @RequestParam(required = false)
                    @Positive
                    Long labelId,
            @Parameter(description = "Filter by task status", example = "TODO")
                    @RequestParam(required = false)
                    TaskStatus status,
            @Parameter(description = "Filter by task priority", example = "HIGH")
                    @RequestParam(required = false)
                    TaskPriority priority,
            @Parameter(description = "Zero-based page number", example = "0")
                    @RequestParam(defaultValue = "0")
                    @Min(0)
                    int page,
            @Parameter(description = "Page size from 1 to 50", example = "20")
                    @RequestParam(defaultValue = "20")
                    @Min(1)
                    @Max(50)
                    int size,
            ServerHttpResponse response) {
        return blocking.call(
                        () -> {
                            ResultPage<TaskResponse> result =
                                    taskService
                                            .findAll(
                                                    new TaskFilter(
                                                            projectId,
                                                            authorId,
                                                            assigneeId,
                                                            labelId,
                                                            status,
                                                            priority),
                                                    PageQuery.of(page, size))
                                            .map(WebMapper::toResponse);
                            response.getHeaders()
                                    .set(
                                            "X-Total-Count",
                                            String.valueOf(result.getTotalElements()));
                            return result.getContent();
                        })
                .flatMapMany(Flux::fromIterable);
    }

    @GetMapping(value = "/feed", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a cursor-based task feed")
    @ApiResponse(
            responseCode = "200",
            description = "Task feed returned",
            headers =
                    @Header(
                            name = "X-Next-Cursor",
                            description = "Last returned task id when another page is available",
                            schema = @Schema(type = "integer", format = "int64")),
            content =
                    @Content(
                            array =
                                    @ArraySchema(
                                            schema = @Schema(implementation = TaskResponse.class))))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid cursor or limit",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Flux<TaskResponse> findFeed(
            @Parameter(
                            description = "Return tasks with an id greater than this cursor",
                            example = "100")
                    @RequestParam(required = false)
                    @Min(0)
                    Long afterId,
            @Parameter(description = "Feed size from 1 to 50", example = "20")
                    @RequestParam(defaultValue = "20")
                    @Min(1)
                    @Max(50)
                    int limit,
            ServerHttpResponse response) {
        return blocking.call(
                        () -> {
                            TaskFeedResponse result =
                                    WebMapper.toResponse(taskService.findFeed(afterId, limit));
                            if (result.nextCursor() != null) {
                                response.getHeaders()
                                        .set("X-Next-Cursor", String.valueOf(result.nextCursor()));
                            }
                            return result.tasks();
                        })
                .flatMapMany(Flux::fromIterable);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a task")
    @ApiResponse(
            responseCode = "200",
            description = "Task updated",
            content = @Content(schema = @Schema(implementation = TaskResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Request validation failed",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Task or related resource not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Task key, version or project change conflict",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Mono<TaskResponse> update(
            @Parameter(description = "Task identifier", example = "1") @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request) {
        return CurrentUser.current()
                .flatMap(
                        actor ->
                                blocking.call(
                                        () ->
                                                WebMapper.toResponse(
                                                        taskService.update(
                                                                id,
                                                                WebMapper.toCommand(request),
                                                                actor))));
    }

    @PostMapping("/{id}/move")
    @Operation(summary = "Move a task to another project")
    @ApiResponse(
            responseCode = "200",
            description = "Task moved",
            content = @Content(schema = @Schema(implementation = TaskResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Request validation failed",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Task or related resource not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Assignee membership or version conflict",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Mono<TaskResponse> move(
            @Parameter(description = "Task identifier", example = "1") @PathVariable Long id,
            @Valid @RequestBody MoveTaskRequest request) {
        return CurrentUser.current()
                .flatMap(
                        actor ->
                                blocking.call(
                                        () ->
                                                WebMapper.toResponse(
                                                        taskService.move(
                                                                id,
                                                                WebMapper.toCommand(request),
                                                                actor))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a task")
    @ApiResponse(responseCode = "204", description = "Task deleted")
    @ApiResponse(
            responseCode = "404",
            description = "Task not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(
            @Parameter(description = "Task identifier", example = "1") @PathVariable Long id) {
        return CurrentUser.current()
                .flatMap(actor -> blocking.run(() -> taskService.delete(id, actor)));
    }
}
