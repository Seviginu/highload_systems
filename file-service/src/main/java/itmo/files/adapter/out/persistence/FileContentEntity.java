package itmo.files.adapter.out.persistence;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.util.UUID;

@Entity
@Table(name = "file_contents")
public class FileContentEntity {
    @Id private UUID id;

    @NotNull
    @Size(min = 1)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] bytes;

    protected FileContentEntity() {}

    public FileContentEntity(UUID id, byte[] bytes) {
        this.id = id;
        this.bytes = bytes;
    }

    public byte[] bytes() {
        return bytes;
    }
}
