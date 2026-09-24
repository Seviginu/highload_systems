package itmo.task.mapper;

import itmo.label.entity.Label;
import itmo.project.entity.Project;
import itmo.task.dto.CreateTaskRequest;
import itmo.task.dto.TaskResponse;
import itmo.task.dto.UpdateTaskRequest;
import itmo.task.entity.Task;
import itmo.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class TaskMapper {

    public Task toEntity(
            CreateTaskRequest request,
            User author,
            User assignee,
            Project project,
            Set<Label> labels
    ) {
        return new Task(
                normalizeTaskKey(request.taskKey()),
                normalizeTitle(request.title()),
                normalizeDescription(request.description()),
                request.status(),
                request.priority(),
                author,
                assignee,
                project,
                labels
        );
    }

    public void updateEntity(
            Task task,
            UpdateTaskRequest request,
            User author,
            User assignee,
            Project project,
            Set<Label> labels
    ) {
        task.update(
                normalizeTaskKey(request.taskKey()),
                normalizeTitle(request.title()),
                normalizeDescription(request.description()),
                request.status(),
                request.priority(),
                author,
                assignee,
                project,
                labels
        );
    }

    public TaskResponse toResponse(Task task) {
        List<Long> labelIds = task.getLabels().stream()
                .map(Label::getId)
                .sorted()
                .toList();
        return new TaskResponse(
                task.getId(),
                task.getTaskKey(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getAuthor().getId(),
                task.getAssignee() == null ? null : task.getAssignee().getId(),
                task.getProject().getId(),
                labelIds,
                task.getVersion(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    public String normalizeTaskKey(String taskKey) {
        return taskKey.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeTitle(String title) {
        return title.trim();
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
