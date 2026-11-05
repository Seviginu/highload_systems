package itmo.user.application.model;

import itmo.user.domain.UserRole;

public record UserReference(Long id, UserRole role) {
}
