package itmo.tracker.application.model;

import java.time.Instant;

public record LabelView(Long id, String name, String color, Instant createdAt) {}
