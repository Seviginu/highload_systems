package itmo.user.service;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.user.dto.CreateUserRequest;
import itmo.user.dto.UpdateUserRequest;
import itmo.user.entity.User;
import itmo.user.entity.UserRole;
import itmo.user.mapper.UserMapper;
import itmo.user.repository.UserRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    @Mock
    private UserRepository repository;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(repository, new UserMapper());
    }

    @Test
    void shouldExecuteCreateOnlyOnSubscription() {
        var result = service.create(new CreateUserRequest(" Alice ", "ALICE@EXAMPLE.COM", UserRole.ADMIN));
        verifyNoInteractions(repository);
        when(repository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(Mono.just(false));
        when(repository.save(any())).thenAnswer(call -> Mono.just(call.getArgument(0, User.class)));
        StepVerifier.create(result).expectNextMatches(user -> user.name().equals("Alice")
                && user.email().equals("alice@example.com")).verifyComplete();
    }

    @Test
    void shouldTranslateConcurrentDatabaseEmailConflict() {
        when(repository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(Mono.just(false));
        when(repository.save(any())).thenReturn(Mono.error(new DataIntegrityViolationException("uq_users_email_lower")));
        StepVerifier.create(service.create(new CreateUserRequest("Alice", "alice@example.com", UserRole.ADMIN)))
                .expectError(ConflictException.class).verify();
    }

    @Test
    void shouldPreserveOtherIntegrityErrors() {
        when(repository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(Mono.just(false));
        when(repository.save(any())).thenReturn(Mono.error(new DataIntegrityViolationException("other constraint")));
        StepVerifier.create(service.create(new CreateUserRequest("Alice", "alice@example.com", UserRole.ADMIN)))
                .expectError(DataIntegrityViolationException.class).verify();
    }

    @Test
    void shouldReportMissingUserReactively() {
        when(repository.findByIdAndDeletedFalse(42L)).thenReturn(Mono.empty());
        StepVerifier.create(service.findById(42L)).expectError(ResourceNotFoundException.class).verify();
    }

    @Test
    void shouldTranslateConcurrentDelete() {
        var user = new User("Alice", "alice@example.com", UserRole.ADMIN);
        when(repository.findByIdAndDeletedFalse(1L)).thenReturn(Mono.just(user));
        when(repository.save(user)).thenReturn(Mono.error(new OptimisticLockingFailureException("stale version")));
        StepVerifier.create(service.delete(1L)).expectError(ConflictException.class).verify();
    }

    @Test
    void shouldTranslateConcurrentUpdate() {
        var user = new User("Alice", "alice@example.com", UserRole.ADMIN);
        when(repository.findByIdAndDeletedFalse(1L)).thenReturn(Mono.just(user));
        when(repository.existsByEmailIgnoreCaseAndIdNot("alice@example.com", 1L)).thenReturn(Mono.just(false));
        when(repository.save(user)).thenReturn(Mono.error(new OptimisticLockingFailureException("stale version")));
        StepVerifier.create(service.update(1L, new UpdateUserRequest("Updated", "alice@example.com", UserRole.ADMIN)))
                .expectError(ConflictException.class).verify();
    }
}
