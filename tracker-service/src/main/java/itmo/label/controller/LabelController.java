package itmo.label.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/labels")
@Tag(name = "Labels", description = "Task label management")
public class LabelController {

    private final LabelService labelService;

    private final BlockingOperations blocking;

    public LabelController(LabelService labelService, BlockingOperations blocking) {
        this.labelService = labelService;
        this.blocking = blocking;
    }

    @PostMapping
    @Operation(summary = "Create a label")
    @ApiResponse(
            responseCode = "201",
            description = "Label created",
            headers = @Header(
                    name = "Location",
                    description = "URI of the created label",
                    schema = @Schema(type = "string", format = "uri")
            ),
            content = @Content(schema = @Schema(implementation = LabelResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Request validation failed",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = "Label name is already used",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<LabelResponse> create(@Valid @RequestBody CreateLabelRequest request, ServerWebExchange exchange) {
        return CurrentUser.current().flatMap(actor -> blocking.call(() -> {
            LabelResponse response = labelService.create(request, actor);
            URI location = UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                    .path("/{id}")
                    .buildAndExpand(response.id())
                    .toUri();
            exchange.getResponse().getHeaders().setLocation(location);
            return response;
        }));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a label by id")
    @ApiResponse(
            responseCode = "200",
            description = "Label found",
            content = @Content(schema = @Schema(implementation = LabelResponse.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "Label not found",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    public Mono<LabelResponse> findById(
            @Parameter(description = "Label identifier", example = "1")
            @PathVariable Long id
    ) {
        return blocking.call(() -> labelService.findById(id));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a page of labels")
    @ApiResponse(
            responseCode = "200",
            description = "Page returned",
            headers = @Header(
                    name = "X-Total-Count",
                    description = "Total number of labels",
                    schema = @Schema(type = "integer", format = "int64")
            ),
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = LabelResponse.class)))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid page or size",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    public Flux<LabelResponse> findAll(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size from 1 to 50", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            ServerHttpResponse response
    ) {
        return blocking.call(() -> {
            Page<LabelResponse> result = labelService.findAll(
                    PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"))
            );
            response.getHeaders().set("X-Total-Count", String.valueOf(result.getTotalElements()));
            return result.getContent();
        }).flatMapMany(Flux::fromIterable);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a label")
    @ApiResponse(
            responseCode = "200",
            description = "Label updated",
            content = @Content(schema = @Schema(implementation = LabelResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Request validation failed",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "Label not found",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = "Label name is already used",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    public Mono<LabelResponse> update(
            @Parameter(description = "Label identifier", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateLabelRequest request
    ) {
        return CurrentUser.current().flatMap(actor -> blocking.call(() -> labelService.update(id, request, actor)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a label")
    @ApiResponse(responseCode = "204", description = "Label deleted")
    @ApiResponse(
            responseCode = "404",
            description = "Label not found",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = "Label is referenced by tasks",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(
            @Parameter(description = "Label identifier", example = "1")
            @PathVariable Long id
    ) {
        return CurrentUser.current().flatMap(actor -> blocking.run(() -> labelService.delete(id, actor)));
    }
}
