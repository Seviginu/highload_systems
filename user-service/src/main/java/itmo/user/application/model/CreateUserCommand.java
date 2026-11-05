package itmo.user.application.model;

import itmo.user.domain.UserRole;

public record CreateUserCommand(String name, String email, UserRole role, String password) {

    @Override
    public String toString() {
        return "CreateUserCommand[name=" + name + ", email=" + email + ", role=" + role + ", password=<redacted>]";
    }
}
