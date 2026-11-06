package itmo.files.adapter.out.persistence;

import itmo.files.application.FileNotFound;
import itmo.files.application.port.Files;
import itmo.files.domain.FileMetadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class S3Files implements Files {
    private static final Logger log = LoggerFactory.getLogger(S3Files.class);
    private final FileRepository files;
    private final FileContentRepository legacyContent;
    private final S3Client s3;
    private final String bucket;

    public S3Files(FileRepository files, FileContentRepository legacyContent,
            S3Client s3, @Value("${app.files.s3.bucket}") String bucket) {
        this.files = files;
        this.legacyContent = legacyContent;
        this.s3 = s3;
        this.bucket = bucket;
    }

    public void save(FileMetadata metadata, byte[] bytes) {
        files.saveAndFlush(new FileEntity(metadata));
        String key = metadata.id().toString();
        // Register before PUT: even a timed-out request may have stored the object.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        s3.deleteObject(r -> r.bucket(bucket).key(key));
                    } catch (RuntimeException e) {
                        log.error("Cannot clean up S3 object after rollback: bucket={}, key={}",
                                bucket, key, e);
                    }
                }
            }
        });
        s3.putObject(r -> r.bucket(bucket).key(key).contentType(metadata.contentType()),
                RequestBody.fromBytes(bytes));
    }

    public Optional<FileMetadata> metadata(UUID id) {
        return files.findById(id).map(FileEntity::toDomain);
    }

    public byte[] content(UUID id) {
        try {
            return s3.getObjectAsBytes(r -> r.bucket(bucket).key(id.toString())).asByteArray();
        } catch (NoSuchKeyException e) {
            // Files uploaded before S3 was introduced remain readable.
            return legacyContent.findById(id).orElseThrow(FileNotFound::new).bytes();
        }
    }
}
