package itmo.files.domain;

import java.time.Instant;
import java.util.UUID;

public record FileMetadata(
        UUID id, long ownerId, String name, String contentType, long size, Instant createdAt) {
    public FileMetadata {
        if (id == null
                || ownerId <= 0
                || name == null
                || name.isBlank()
                || name.length() > 255
                || contentType == null
                || contentType.isBlank()
                || contentType.length() > 255
                || size <= 0
                || createdAt == null) throw new IllegalArgumentException("Invalid file metadata");
    }
}
