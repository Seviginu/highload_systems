package itmo.user.application.model;

import itmo.user.domain.UserRole;

public record UpdateUserCommand(String name, String email, UserRole role) {
}
