package itmo.tracker.application.model;

import java.util.List;

public record TaskFeedView(List<TaskView> tasks, Long nextCursor) {}
