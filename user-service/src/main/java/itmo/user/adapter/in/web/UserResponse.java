package itmo.user.adapter.in.web;

import itmo.user.domain.UserRole;

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

