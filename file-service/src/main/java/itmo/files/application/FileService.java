package itmo.files.application;

import itmo.files.application.port.*;
import itmo.files.domain.FileMetadata;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

public class FileService {
    private final Files files;
    private final FileEvents events;
    private final UnitOfWork transactions;
    private final Clock clock;
    private final Supplier<UUID> ids;
    private final int maxBytes;

    public FileService(
            Files files,
            FileEvents events,
            UnitOfWork transactions,
            Clock clock,
            Supplier<UUID> ids,
            int maxBytes) {
        this.files = files;
        this.events = events;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
        this.maxBytes = maxBytes;
    }

    public FileMetadata upload(long owner, String name, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new InvalidFile();
        if (bytes.length > maxBytes) throw new FileTooLarge();
        var metadata =
                new FileMetadata(
                        ids.get(), owner, name, contentType, bytes.length, clock.instant());
        return transactions.write(
                () -> {
                    files.save(metadata, bytes);
                    events.append(metadata);
                    return metadata;
                });
    }

    public FileMetadata metadata(UUID id, Actor actor) {
        return transactions.read(() -> accessible(id, actor));
    }

    public FileDownload download(UUID id, Actor actor) {
        return transactions.read(() -> new FileDownload(accessible(id, actor), files.content(id)));
    }

    private FileMetadata accessible(UUID id, Actor actor) {
        var metadata = files.metadata(id).orElseThrow(FileNotFound::new);
        if (metadata.ownerId() != actor.id() && !actor.admin()) throw new FileForbidden();
        return metadata;
    }
}
