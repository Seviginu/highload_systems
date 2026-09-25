package itmo.common.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class ConstraintViolationDetector {

    private ConstraintViolationDetector() {
    }

    public static boolean isViolationOf(
            DataIntegrityViolationException exception,
            String... constraintNames
    ) {
        Set<String> expectedNames = new HashSet<>(Arrays.asList(constraintNames));
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && expectedNames.contains(violation.getConstraintName())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && expectedNames.stream().anyMatch(message::contains)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
