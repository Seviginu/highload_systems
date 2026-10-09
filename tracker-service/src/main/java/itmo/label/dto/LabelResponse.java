package itmo.label.dto;

import java.time.Instant;

public record LabelResponse(
        Long id,
        String name,
        String color,
        Instant createdAt
) {
}
