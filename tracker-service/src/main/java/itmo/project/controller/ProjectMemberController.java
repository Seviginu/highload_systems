package itmo.project.controller;

import itmo.infrastructure.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import itmo.common.web.ApiError;
import itmo.infrastructure.reactor.BlockingOperations;
import itmo.project.dto.AddProjectMemberRequest;
import itmo.project.dto.ProjectMemberResponse;
import itmo.project.service.ProjectMemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
@RequestMapping("/api/v1/projects/{projectId}/members")
@Tag(name = "Project members", description = "Project membership management")
public class ProjectMemberController {

    private final ProjectMemberService memberService;

    private final BlockingOperations blocking;

    public ProjectMemberController(ProjectMemberService memberService, BlockingOperations blocking) {
        this.memberService = memberService;
        this.blocking = blocking;
    }

    @PostMapping
    @Operation(summary = "Add a member to a project")
    @ApiResponse(
            responseCode = "201",
            description = "Member added",
            headers = @Header(
                    name = "Location",
                    description = "URI of the created project membership",
                    schema = @Schema(type = "string", format = "uri")
            ),
            content = @Content(schema = @Schema(implementation = ProjectMemberResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Request validation failed",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid page or size",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "Project or user not found",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = "User is already an active member",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ProjectMemberResponse> add(
            @Parameter(description = "Project identifier", example = "1")
            @PathVariable Long projectId,
            @Valid @RequestBody AddProjectMemberRequest request,
            ServerWebExchange exchange
    ) {
        return CurrentUser.current().flatMap(actor -> blocking.call(() -> {
            ProjectMemberResponse response = memberService.add(projectId, request, actor);
            URI location = UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                    .path("/{id}")
                    .buildAndExpand(response.id())
                    .toUri();
            exchange.getResponse().getHeaders().setLocation(location);
            return response;
        }));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a page of project members")
    @ApiResponse(
            responseCode = "200",
            description = "Page returned",
            headers = @Header(
                    name = "X-Total-Count",
                    description = "Total number of project members",
                    schema = @Schema(type = "integer", format = "int64")
            ),
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProjectMemberResponse.class)))
    )
    @ApiResponse(
            responseCode = "404",
            description = "Project not found",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    public Flux<ProjectMemberResponse> findAll(
            @Parameter(description = "Project identifier", example = "1")
            @PathVariable Long projectId,
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size from 1 to 50", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            ServerHttpResponse response
    ) {
        return blocking.call(() -> {
            Page<ProjectMemberResponse> result = memberService.findAll(
                    projectId,
                    PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"))
            );
            response.getHeaders().set("X-Total-Count", String.valueOf(result.getTotalElements()));
            return result.getContent();
        }).flatMapMany(Flux::fromIterable);
    }

    @DeleteMapping("/{memberId}")
    @Operation(summary = "Deactivate a project member")
    @ApiResponse(responseCode = "204", description = "Member deactivated")
    @ApiResponse(
            responseCode = "404",
            description = "Project or member not found",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deactivate(
            @Parameter(description = "Project identifier", example = "1")
            @PathVariable Long projectId,
            @Parameter(description = "Project membership identifier", example = "1")
            @PathVariable Long memberId
    ) {
        return CurrentUser.current().flatMap(actor -> blocking.run(() -> memberService.deactivate(projectId, memberId, actor)));
    }
}
