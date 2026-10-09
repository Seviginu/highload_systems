package itmo.common.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ConstraintViolationDetectorTest {

    @Test
    void shouldFindExpectedConstraintInCauseChain() {
        var hibernateException = new ConstraintViolationException(
                "constraint violation",
                new SQLException("duplicate"),
                "uq_tasks_task_key"
        );
        var exception = new DataIntegrityViolationException("write failed", hibernateException);

        assertThat(ConstraintViolationDetector.isViolationOf(exception, "uq_tasks_task_key")).isTrue();
        assertThat(ConstraintViolationDetector.isViolationOf(exception, "fk_tasks_project")).isFalse();
    }
}
