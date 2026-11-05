package itmo.user.adapter.out.persistence;

import itmo.user.application.exception.ConflictException;
import itmo.user.domain.User;
import itmo.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class R2dbcUsersTest {
    @Mock UserRepository repository;
    R2dbcUsers users;
    final User user = new User("Alice", "alice@example.com", UserRole.DEVELOPER);
    @BeforeEach void setUp() { users = new R2dbcUsers(repository); }

    @Test
    void shouldTranslateConcurrentDatabaseEmailConflict() {
        when(repository.save(any())).thenReturn(Mono.error(new DataIntegrityViolationException("uq_users_email_lower")));
        StepVerifier.create(users.save(user)).expectErrorMatches(error -> error instanceof ConflictException
                && error.getMessage().contains("alice@example.com")).verify();
    }
    @Test
    void shouldTranslateSynchronousRepositoryFailureAndNestedConstraint() {
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("insert failed",
                new IllegalStateException("uq_users_email_lower")));
        StepVerifier.create(users.save(user)).expectError(ConflictException.class).verify();
    }
    @Test
    void shouldPreserveOtherIntegrityErrors() {
        var error = new DataIntegrityViolationException("other constraint");
        when(repository.save(any())).thenReturn(Mono.error(error));
        StepVerifier.create(users.save(user)).expectErrorMatches(actual -> actual == error).verify();
    }
    @Test
    void shouldTranslateOptimisticLockConflict() {
        when(repository.save(any())).thenReturn(Mono.error(new OptimisticLockingFailureException("stale version")));
        StepVerifier.create(users.save(user)).expectError(ConflictException.class).verify();
    }
}
