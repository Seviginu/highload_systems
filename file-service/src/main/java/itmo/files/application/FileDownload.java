package itmo.files.application;

import itmo.files.domain.FileMetadata;

public record FileDownload(FileMetadata metadata, byte[] bytes) {}
