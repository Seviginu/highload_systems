package itmo.files.adapter.in.web;

import itmo.files.domain.FileMetadata;

import java.time.Instant;
import java.util.UUID;

public record FileResponse(
        UUID id, long ownerId, String name, String contentType, long size, Instant createdAt) {
    public static FileResponse from(FileMetadata m) {
        return new FileResponse(
                m.id(), m.ownerId(), m.name(), m.contentType(), m.size(), m.createdAt());
    }
}
