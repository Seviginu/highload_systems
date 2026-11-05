package itmo.files.application.port;

import itmo.files.domain.FileMetadata;

public interface FileEvents {
    void append(FileMetadata metadata);
}
