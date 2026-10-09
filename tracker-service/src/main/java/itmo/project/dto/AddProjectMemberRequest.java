package itmo.project.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddProjectMemberRequest(
        @NotNull(message = "User is required")
        @Positive(message = "User id must be positive")
        Long userId
) {
}
