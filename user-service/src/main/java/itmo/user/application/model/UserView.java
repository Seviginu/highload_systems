package itmo.user.application.model;

import itmo.user.domain.UserRole;
import java.time.Instant;

public record UserView(Long id, String name, String email, UserRole role, Instant createdAt, Instant updatedAt) {

    public static UserView from(itmo.user.domain.User user) {
        return new UserView(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
