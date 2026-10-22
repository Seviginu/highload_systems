package itmo.user.service;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.user.dto.CreateUserRequest;
import itmo.user.dto.UpdateUserRequest;
import itmo.user.dto.UserResponse;
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
import org.springframework.data.domain.PageRequest;

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
    private UserRepository userRepository;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, new UserMapper());
    }

    @Test
    void shouldCreateUserWithNormalizedValues() {
        CreateUserRequest request = new CreateUserRequest(
                " Alice ",
                "Alice@Example.COM",
                UserRole.DEVELOPER
        );
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(Mono.just(false));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0, User.class)));

        var response = userService.create(request).block();

        assertThat(response.name()).isEqualTo("Alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldRejectDuplicateEmail() {
        CreateUserRequest request = new CreateUserRequest(
                "Alice",
                "Alice@Example.COM",
                UserRole.DEVELOPER
        );
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
        PageRequest pageable = PageRequest.of(0, 20);
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
        UpdateUserRequest request = new UpdateUserRequest(
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
        UpdateUserRequest request = new UpdateUserRequest(
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
    void shouldTranslateDatabaseEmailConflict() {
        CreateUserRequest request = new CreateUserRequest(
                "Alice",
                "alice@example.com",
                UserRole.DEVELOPER
        );
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(Mono.just(false));
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uq_users_email_lower"));

        assertThatThrownBy(() -> userService.create(request).block())
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("alice@example.com");
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
