package itmo.label.controller;

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
import itmo.label.dto.CreateLabelRequest;
import itmo.label.dto.LabelResponse;
import itmo.label.dto.UpdateLabelRequest;
import itmo.label.service.LabelService;
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
@RequestMapping("/api/labels")
@Tag(name = "Labels", description = "Task label management")
public class LabelController {

    private final LabelService labelService;

    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @PostMapping
    @Operation(summary = "Create a label")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Label created",
                    headers = @Header(
                            name = "Location",
                            description = "URI of the created label",
                            schema = @Schema(type = "string", format = "uri")
                    ),
                    content = @Content(schema = @Schema(implementation = LabelResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Label name is already used",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public ResponseEntity<LabelResponse> create(@Valid @RequestBody CreateLabelRequest request) {
        LabelResponse response = labelService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a label by id")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Label found",
                    content = @Content(schema = @Schema(implementation = LabelResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Label not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public LabelResponse findById(
            @Parameter(description = "Label identifier", example = "1")
            @PathVariable Long id
    ) {
        return labelService.findById(id);
    }

    @GetMapping
    @Operation(summary = "Get a page of labels")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Page returned",
                    headers = @Header(
                            name = "X-Total-Count",
                            description = "Total number of labels",
                            schema = @Schema(type = "integer", format = "int64")
                    ),
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = LabelResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid page or size",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public ResponseEntity<List<LabelResponse>> findAll(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size from 1 to 50", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        Page<LabelResponse> result = labelService.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"))
        );
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(result.getTotalElements()))
                .body(result.getContent());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a label")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Label updated",
                    content = @Content(schema = @Schema(implementation = LabelResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Label not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Label name is already used",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public LabelResponse update(
            @Parameter(description = "Label identifier", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateLabelRequest request
    ) {
        return labelService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a label")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Label deleted"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Label not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Label is referenced by tasks",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Label identifier", example = "1")
            @PathVariable Long id
    ) {
        labelService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
