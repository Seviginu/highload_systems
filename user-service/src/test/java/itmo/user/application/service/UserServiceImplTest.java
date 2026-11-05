package itmo.user.application.service;

import itmo.user.application.exception.ConflictException;
import itmo.user.application.exception.ResourceNotFoundException;
import itmo.user.application.model.CreateUserCommand;
import itmo.user.application.model.UpdateUserCommand;
import itmo.user.domain.User;
import itmo.user.domain.UserRole;
import itmo.user.application.port.out.Users;
import itmo.user.application.port.out.Passwords;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    @Mock
    private Users repository;
    @Mock
    private Passwords passwords;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(repository,
                passwords);
    }

    @Test
    void shouldExecuteCreateOnlyOnSubscription() {
        var result = service.create(new CreateUserCommand(" Alice ", "ALICE@EXAMPLE.COM", UserRole.ADMIN, "Test-password-123"));
        verifyNoInteractions(repository);
        when(repository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(Mono.just(false));
        when(passwords.encode("Test-password-123")).thenReturn(Mono.just("encoded-password"));
        when(repository.save(any())).thenAnswer(call -> Mono.just(call.getArgument(0, User.class)));
        StepVerifier.create(result).expectNextMatches(user -> user.name().equals("Alice")
                && user.email().equals("alice@example.com")).verifyComplete();
    }

    @Test
    void shouldReportMissingUserReactively() {
        when(repository.findByIdAndDeletedFalse(42L)).thenReturn(Mono.empty());
        StepVerifier.create(service.findById(42L)).expectError(ResourceNotFoundException.class).verify();
    }

    @Test
    void shouldPropagateConcurrentDelete() {
        var user = new User("Alice", "alice@example.com", UserRole.ADMIN);
        when(repository.findByIdAndDeletedFalse(1L)).thenReturn(Mono.just(user));
        when(repository.save(user)).thenReturn(Mono.error(new ConflictException("stale version")));
        StepVerifier.create(service.delete(1L)).expectError(ConflictException.class).verify();
    }

    @Test
    void shouldPropagateConcurrentUpdate() {
        var user = new User("Alice", "alice@example.com", UserRole.ADMIN);
        when(repository.findByIdAndDeletedFalse(1L)).thenReturn(Mono.just(user));
        when(repository.existsByEmailIgnoreCaseAndIdNot("alice@example.com", 1L)).thenReturn(Mono.just(false));
        when(repository.save(user)).thenReturn(Mono.error(new ConflictException("stale version")));
        StepVerifier.create(service.update(1L, new UpdateUserCommand("Updated", "alice@example.com", UserRole.ADMIN)))
                .expectError(ConflictException.class).verify();
    }
}
