package itmo.tracker.adapter.out.persistence;

import itmo.tracker.application.exception.ConflictException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.function.Supplier;

final class StorageErrors {
    private StorageErrors() {}

    static <T> T run(
            Supplier<T> operation, String constraint, String message, String versionMessage) {
        try {
            return operation.get();
        } catch (DataIntegrityViolationException error) {
            if (ConstraintViolationDetector.isViolationOf(error, constraint)) {
                throw new ConflictException(message);
            }
            throw error;
        } catch (OptimisticLockingFailureException error) {
            if (versionMessage != null) {
                throw new ConflictException(versionMessage);
            }
            throw error;
        }
    }
}
