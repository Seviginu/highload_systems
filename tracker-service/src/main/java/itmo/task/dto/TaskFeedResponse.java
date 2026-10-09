package itmo.task.dto;

import java.util.List;

public record TaskFeedResponse(
        List<TaskResponse> tasks,
        Long nextCursor
) {
}
