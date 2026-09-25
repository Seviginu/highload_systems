package itmo.project.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import itmo.common.web.ApiError;
import itmo.project.dto.CreateProjectRequest;
import itmo.project.dto.ProjectResponse;
import itmo.project.dto.UpdateProjectRequest;
import itmo.project.service.ProjectService;
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
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Project management")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @Operation(summary = "Create a project")
    @ApiResponse(
            responseCode = "201",
            description = "Project created",
            headers = @Header(
                    name = "Location",
                    description = "URI of the created project",
                    schema = @Schema(type = "string", format = "uri")
            ),
            content = @Content(schema = @Schema(implementation = ProjectResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Request validation failed",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = "Project code is already used",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse response = projectService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a project by id")
    @ApiResponse(
            responseCode = "200",
            description = "Project found",
            content = @Content(schema = @Schema(implementation = ProjectResponse.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "Project not found",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    public ProjectResponse findById(
            @Parameter(description = "Project identifier", example = "1")
            @PathVariable Long id
    ) {
        return projectService.findById(id);
    }

    @GetMapping
    @Operation(summary = "Get a page of projects")
    @ApiResponse(
            responseCode = "200",
            description = "Page returned",
            headers = @Header(
                    name = "X-Total-Count",
                    description = "Total number of projects",
                    schema = @Schema(type = "integer", format = "int64")
            ),
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProjectResponse.class)))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid page or size",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    public ResponseEntity<List<ProjectResponse>> findAll(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size from 1 to 50", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        Page<ProjectResponse> result = projectService.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"))
        );
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(result.getTotalElements()))
                .body(result.getContent());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a project")
    @ApiResponse(
            responseCode = "200",
            description = "Project updated",
            content = @Content(schema = @Schema(implementation = ProjectResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Request validation failed",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "Project not found",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = "Project code is already used",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    public ProjectResponse update(
            @Parameter(description = "Project identifier", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        return projectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a project")
    @ApiResponse(responseCode = "204", description = "Project deleted")
    @ApiResponse(
            responseCode = "404",
            description = "Project not found",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = "Project is referenced by another record",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    public ResponseEntity<Void> delete(
            @Parameter(description = "Project identifier", example = "1")
            @PathVariable Long id
    ) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
