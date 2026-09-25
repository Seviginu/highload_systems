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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

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
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userService.create(request);

        assertThat(response.name()).isEqualTo("Alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void shouldRejectDuplicateEmail() {
        CreateUserRequest request = new CreateUserRequest(
                "Alice",
                "Alice@Example.COM",
                UserRole.DEVELOPER
        );
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("alice@example.com");
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void shouldReportMissingUser() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void shouldReturnPageOfUsers() {
        User user = new User("Alice", "alice@example.com", UserRole.DEVELOPER);
        PageRequest pageable = PageRequest.of(0, 20);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user), pageable, 1));

        var result = userService.findAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(UserResponse::email)
                .containsExactly("alice@example.com");
    }

    @Test
    void shouldUpdateUser() {
        User user = new User("Alice", "alice@example.com", UserRole.DEVELOPER);
        UpdateUserRequest request = new UpdateUserRequest(
                "Alice Lead",
                "ALICE.LEAD@EXAMPLE.COM",
                UserRole.TEAM_LEAD
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("alice.lead@example.com", 1L))
                .thenReturn(false);
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        var response = userService.update(1L, request);

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
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("busy@example.com", 1L)).thenReturn(true);

        assertThatThrownBy(() -> userService.update(1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("busy@example.com");
        verify(userRepository, never()).saveAndFlush(user);
    }

    @Test
    void shouldTranslateDatabaseEmailConflict() {
        CreateUserRequest request = new CreateUserRequest(
                "Alice",
                "alice@example.com",
                UserRole.DEVELOPER
        );
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("alice@example.com");
    }

    @Test
    void shouldDeleteExistingUser() {
        User user = new User("Alice", "alice@example.com", UserRole.DEVELOPER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.delete(1L);

        verify(userRepository).delete(user);
        verify(userRepository).flush();
    }

    @Test
    void shouldRejectDeletionOfReferencedUser() {
        User user = new User("Alice", "alice@example.com", UserRole.DEVELOPER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new DataIntegrityViolationException("foreign key"))
                .when(userRepository).flush();

        assertThatThrownBy(() -> userService.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("referenced");
    }
}
