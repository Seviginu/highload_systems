package itmo.files.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;

import itmo.files.application.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.core.io.buffer.*;
import org.springframework.http.*;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final FileService service;
    private final Scheduler worker;
    private final int max;

    public FileController(
            FileService service,
            @Qualifier("fileWorker") Scheduler worker,
            @Value("${app.files.max-file-bytes:10485760}") int max) {
        this.service = service;
        this.worker = worker;
        this.max = max;
    }

    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Upload a file; owner comes from JWT")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<FileResponse>> upload(
            @AuthenticationPrincipal Jwt jwt, @RequestPart("file") FilePart file) {
        long owner = Long.parseLong(jwt.getSubject());
        String name = safeName(file.filename());
        String type = contentType(file);
        return Mono.usingWhen(
                Mono.just(file),
                part ->
                        readContent(part)
                                .flatMap(
                                        bytes ->
                                                Mono.fromCallable(
                                                                () ->
                                                                        service.upload(
                                                                                owner, name, type,
                                                                                bytes))
                                                        .subscribeOn(worker))
                                .map(
                                        m ->
                                                ResponseEntity.created(
                                                                URI.create(
                                                                        "/api/v1/files/" + m.id()))
                                                        .body(FileResponse.from(m))),
                FilePart::delete);
    }

    private Mono<byte[]> readContent(FilePart part) {
        return DataBufferUtils.join(part.content(), max)
                .map(
                        buffer -> {
                            try {
                                var bytes = new byte[buffer.readableByteCount()];
                                buffer.read(bytes);
                                return bytes;
                            } finally {
                                DataBufferUtils.release(buffer);
                            }
                        })
                .defaultIfEmpty(new byte[0]);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get metadata; owner or ADMIN")
    public Mono<FileResponse> metadata(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return Mono.fromCallable(() -> FileResponse.from(service.metadata(id, actor(jwt))))
                .subscribeOn(worker);
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "Download attachment; owner or ADMIN")
    public Mono<ResponseEntity<byte[]>> content(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return Mono.fromCallable(
                        () -> {
                            var download = service.download(id, actor(jwt));
                            var metadata = download.metadata();
                            return ResponseEntity.ok()
                                    .contentType(MediaType.parseMediaType(metadata.contentType()))
                                    .contentLength(metadata.size())
                                    .header(
                                            HttpHeaders.CONTENT_DISPOSITION,
                                            ContentDisposition.attachment()
                                                    .filename(
                                                            metadata.name(), StandardCharsets.UTF_8)
                                                    .build()
                                                    .toString())
                                    .header("X-Content-Type-Options", "nosniff")
                                    .body(download.bytes());
                        })
                .subscribeOn(worker);
    }

    private Actor actor(Jwt jwt) {
        return new Actor(
                Long.parseLong(jwt.getSubject()),
                jwt.getClaimAsStringList("roles").contains("ADMIN"));
    }

    private String safeName(String original) {
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").strip();
        if (name.isBlank() || name.equals(".") || name.equals("..")) name = "file";
        int end = Math.min(255, name.length());
        if (Character.isHighSurrogate(name.charAt(end - 1))) end--;
        return name.substring(0, end);
    }

    private String contentType(FilePart part) {
        var type = part.headers().getContentType();
        return type == null || !type.isConcrete() || type.toString().length() > 255
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : type.toString();
    }
}
