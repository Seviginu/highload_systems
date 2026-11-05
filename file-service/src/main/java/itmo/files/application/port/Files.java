package itmo.files.application.port;

import itmo.files.domain.FileMetadata;

import java.util.*;

public interface Files {
    void save(FileMetadata metadata, byte[] bytes);

    Optional<FileMetadata> metadata(UUID id);

    byte[] content(UUID id);
}
