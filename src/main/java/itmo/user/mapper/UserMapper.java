package itmo.user.mapper;

import itmo.user.dto.CreateUserRequest;
import itmo.user.dto.UpdateUserRequest;
import itmo.user.dto.UserResponse;
import itmo.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class UserMapper {

    public User toEntity(CreateUserRequest request) {
        return new User(normalizeName(request.name()), normalizeEmail(request.email()), request.role());
    }

    public void updateEntity(User user, UpdateUserRequest request) {
        user.update(normalizeName(request.name()), normalizeEmail(request.email()), request.role());
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String name) {
        return name.trim();
    }
}

