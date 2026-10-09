package itmo.user.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record ResolveUsersRequest(@NotEmpty @Size(max = 50) Set<@NotNull @Positive Long> ids) {
}
