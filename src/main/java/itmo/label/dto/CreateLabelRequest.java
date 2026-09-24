package itmo.label.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateLabelRequest(
        @NotBlank(message = "Name must not be blank")
        @Size(max = 50, message = "Name must not exceed 50 characters")
        String name,

        @Pattern(
                regexp = "^#[0-9A-Fa-f]{6}$",
                message = "Color must use hexadecimal format #RRGGBB"
        )
        String color
) {
}
