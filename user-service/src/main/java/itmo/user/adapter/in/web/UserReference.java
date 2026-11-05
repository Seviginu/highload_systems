package itmo.user.adapter.in.web;

import itmo.user.domain.UserRole;

public record UserReference(Long id, UserRole role) {
}
