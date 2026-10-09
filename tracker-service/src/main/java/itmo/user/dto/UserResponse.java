package itmo.user.dto;

import itmo.user.entity.UserRole;

import java.time.Instant;

public record UserResponse(
        Long id,
        String name,
        String email,
        UserRole role,
        Instant createdAt,
        Instant updatedAt
) {
}

