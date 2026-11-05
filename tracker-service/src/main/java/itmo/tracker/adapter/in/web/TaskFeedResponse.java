package itmo.tracker.adapter.in.web;

import java.util.List;

public record TaskFeedResponse(List<TaskResponse> tasks, Long nextCursor) {}
