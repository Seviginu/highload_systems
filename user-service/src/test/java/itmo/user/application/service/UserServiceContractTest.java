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
import itmo.user.application.model.UserPage;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceContractTest {

    @Mock
    private Users userRepository;

    @Mock
    private Passwords passwords;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository,
                passwords);
    }

    @Test
    void shouldCreateUserWithNormalizedValues() {
        CreateUserCommand request = new CreateUserCommand(
                " Alice ",
                "Alice@Example.COM",
                UserRole.DEVELOPER, "Test-password-123");
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(Mono.just(false));
        when(passwords.encode("Test-password-123")).thenReturn(Mono.just("encoded-password"));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0, User.class)));

        var response = userService.create(request).block();

        assertThat(response.name()).isEqualTo("Alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldRejectDuplicateEmail() {
        CreateUserCommand request = new CreateUserCommand(
                "Alice",
                "Alice@Example.COM",
                UserRole.DEVELOPER, "Test-password-123");
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(Mono.just(true));

        assertThatThrownBy(() -> userService.create(request).block())
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("alice@example.com");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldReportMissingUser() {
        when(userRepository.findByIdAndDeletedFalse(42L)).thenReturn(Mono.empty());

        assertThatThrownBy(() -> userService.findById(42L).block())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void shouldStreamUsersWithDemandAndCancellation() {
        User user = new User("Alice", "alice@example.com", UserRole.DEVELOPER);
        UserPage pageable = new UserPage(0, 20);
        User another = new User("Bob", "bob@example.com", UserRole.DEVELOPER);
        when(userRepository.findActivePage(20, 0)).thenReturn(Flux.just(user, another));
        StepVerifier.create(userService.findAll(pageable), 0)
                .thenRequest(1)
                .assertNext(response -> assertThat(response.email()).isEqualTo("alice@example.com"))
                .thenCancel().verify();
        verify(userRepository, never()).countByDeletedFalse();
    }

    @Test
    void shouldCountActiveUsersSeparately() {
        when(userRepository.countByDeletedFalse()).thenReturn(Mono.just(1L));
        StepVerifier.create(userService.count()).expectNext(1L).verifyComplete();
    }

    @Test
    void shouldUpdateUser() {
        User user = new User("Alice", "alice@example.com", UserRole.DEVELOPER);
        UpdateUserCommand request = new UpdateUserCommand(
                "Alice Lead",
                "ALICE.LEAD@EXAMPLE.COM",
                UserRole.TEAM_LEAD
        );
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Mono.just(user));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("alice.lead@example.com", 1L))
                .thenReturn(Mono.just(false));
        when(userRepository.save(user)).thenReturn(Mono.just(user));

        var response = userService.update(1L, request).block();

        assertThat(response.name()).isEqualTo("Alice Lead");
        assertThat(response.email()).isEqualTo("alice.lead@example.com");
        assertThat(response.role()).isEqualTo(UserRole.TEAM_LEAD);
    }

    @Test
    void shouldRejectDuplicateEmailDuringUpdate() {
        User user = new User("Alice", "alice@example.com", UserRole.DEVELOPER);
        UpdateUserCommand request = new UpdateUserCommand(
                "Alice",
                "busy@example.com",
                UserRole.DEVELOPER
        );
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Mono.just(user));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("busy@example.com", 1L)).thenReturn(Mono.just(true));

        assertThatThrownBy(() -> userService.update(1L, request).block())
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("busy@example.com");
        verify(userRepository, never()).save(user);
    }

    @Test
    void shouldDeleteExistingUser() {
        User user = new User("Alice", "alice@example.com", UserRole.DEVELOPER);
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Mono.just(user));

        when(userRepository.save(user)).thenReturn(Mono.just(user));
        userService.delete(1L).block();
        assertThat(user.isDeleted()).isTrue();
        verify(userRepository).save(user);
    }

}
