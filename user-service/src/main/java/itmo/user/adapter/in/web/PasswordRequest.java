package itmo.user.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record PasswordRequest(@ValidPassword @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String password) {
    @Override
    public String toString() {
        return "PasswordRequest[password=<redacted>]";
    }
}
