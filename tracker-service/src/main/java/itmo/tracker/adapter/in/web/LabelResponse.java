package itmo.tracker.adapter.in.web;

import java.time.Instant;

public record LabelResponse(Long id, String name, String color, Instant createdAt) {}
