package itmo.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(@NotBlank @Email @Size(max = 320) String email,
                           @NotBlank @Size(max = 72) @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String password) {
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=<redacted>]";
    }
}
