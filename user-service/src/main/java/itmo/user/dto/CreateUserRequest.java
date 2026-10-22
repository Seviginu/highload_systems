package itmo.user.dto;

import itmo.user.entity.UserRole;
import itmo.user.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "Name must not be blank")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be valid")
        @Size(max = 320, message = "Email must not exceed 320 characters")
        String email,

        @NotNull(message = "Role is required")
        UserRole role,

        @ValidPassword
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password", minLength = 8,
                description = "At least 8 characters and at most 72 UTF-8 bytes")
        String password
) {
    @Override
    public String toString() {
        return "CreateUserRequest[name=" + name + ", email=" + email + ", role=" + role + ", password=<redacted>]";
    }
}

