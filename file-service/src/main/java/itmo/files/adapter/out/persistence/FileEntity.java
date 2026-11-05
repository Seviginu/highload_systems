package itmo.files.adapter.out.persistence;

import itmo.files.domain.FileMetadata;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "files")
public class FileEntity {
    @Id private UUID id;

    @Positive
    @Column(nullable = false)
    private long ownerId;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String contentType;

    @Positive
    @Column(nullable = false)
    private long size;

    @NotNull
    @Column(nullable = false)
    private Instant createdAt;

    protected FileEntity() {}

    public FileEntity(FileMetadata m) {
        id = m.id();
        ownerId = m.ownerId();
        name = m.name();
        contentType = m.contentType();
        size = m.size();
        createdAt = m.createdAt();
    }

    public FileMetadata toDomain() {
        return new FileMetadata(id, ownerId, name, contentType, size, createdAt);
    }
}
