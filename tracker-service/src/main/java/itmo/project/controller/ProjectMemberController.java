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
import itmo.project.dto.AddProjectMemberRequest;
import itmo.project.dto.ProjectMemberResponse;
import itmo.project.service.ProjectMemberService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
@Tag(name = "Project members", description = "Project membership management")
public class ProjectMemberController {

    private final ProjectMemberService memberService;

    public ProjectMemberController(ProjectMemberService memberService) {
        this.memberService = memberService;
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
    public ResponseEntity<ProjectMemberResponse> add(
            @Parameter(description = "Project identifier", example = "1")
            @PathVariable Long projectId,
            @Valid @RequestBody AddProjectMemberRequest request
    ) {
        ProjectMemberResponse response = memberService.add(projectId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
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
    public ResponseEntity<List<ProjectMemberResponse>> findAll(
            @Parameter(description = "Project identifier", example = "1")
            @PathVariable Long projectId,
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size from 1 to 50", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        Page<ProjectMemberResponse> result = memberService.findAll(
                projectId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"))
        );
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(result.getTotalElements()))
                .body(result.getContent());
    }

    @DeleteMapping("/{memberId}")
    @Operation(summary = "Deactivate a project member")
    @ApiResponse(responseCode = "204", description = "Member deactivated")
    @ApiResponse(
            responseCode = "404",
            description = "Project or member not found",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    public ResponseEntity<Void> deactivate(
            @Parameter(description = "Project identifier", example = "1")
            @PathVariable Long projectId,
            @Parameter(description = "Project membership identifier", example = "1")
            @PathVariable Long memberId
    ) {
        memberService.deactivate(projectId, memberId);
        return ResponseEntity.noContent().build();
    }
}
