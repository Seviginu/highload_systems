package itmo.files.adapter.out.persistence;

import itmo.files.application.FileNotFound;
import itmo.files.application.port.Files;
import itmo.files.domain.FileMetadata;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class JpaFiles implements Files {
    private final FileRepository files;
    private final FileContentRepository content;

    public JpaFiles(FileRepository files, FileContentRepository content) {
        this.files = files;
        this.content = content;
    }

    public void save(FileMetadata metadata, byte[] bytes) {
        files.saveAndFlush(new FileEntity(metadata));
        content.saveAndFlush(new FileContentEntity(metadata.id(), bytes));
    }

    public Optional<FileMetadata> metadata(UUID id) {
        return files.findById(id).map(FileEntity::toDomain);
    }

    public byte[] content(UUID id) {
        return content.findById(id).orElseThrow(FileNotFound::new).bytes();
    }
}
