package itmo.project.dto;

import itmo.project.entity.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank(message = "Name must not be blank")
        @Size(max = 150, message = "Name must not exceed 150 characters")
        String name,

        @NotBlank(message = "Code must not be blank")
        @Size(min = 2, max = 20, message = "Code must contain from 2 to 20 characters")
        @Pattern(
                regexp = "^[A-Za-z]\\w{1,19}$",
                message = "Code must start with a letter and contain only letters, digits or underscores"
        )
        String code,

        String description,

        @NotNull(message = "Status is required")
        ProjectStatus status,

        @NotNull(message = "Team lead is required")
        @Positive(message = "Team lead id must be positive")
        Long teamLeadId
) {
}
