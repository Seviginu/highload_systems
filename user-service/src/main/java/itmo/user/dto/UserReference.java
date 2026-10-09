package itmo.user.dto;

import itmo.user.entity.UserRole;

public record UserReference(Long id, UserRole role) {
}
