package itmo.tracker.application.mapper;

import itmo.tracker.application.model.CreateTaskCommand;
import itmo.tracker.application.model.TaskView;
import itmo.tracker.application.model.UpdateTaskCommand;
import itmo.tracker.domain.Label;
import itmo.tracker.domain.Project;
import itmo.tracker.domain.Task;

import java.util.List;
import java.util.Set;

public class TaskMapper {

    public Task toDomain(
            CreateTaskCommand request,
            Long authorId,
            Long assigneeId,
            Project project,
            Set<Label> labels) {
        return new Task(
                normalizeTaskKey(request.taskKey()),
                normalizeTitle(request.title()),
                normalizeDescription(request.description()),
                request.status(),
                request.priority(),
                authorId,
                assigneeId,
                project,
                labels);
    }

    public void updateDomain(
            Task task,
            UpdateTaskCommand request,
            Long authorId,
            Long assigneeId,
            Project project,
            Set<Label> labels) {
        task.update(
                normalizeTaskKey(request.taskKey()),
                normalizeTitle(request.title()),
                normalizeDescription(request.description()),
                request.status(),
                request.priority(),
                authorId,
                assigneeId,
                project,
                labels);
    }

    public TaskView toResponse(Task task) {
        List<Long> labelIds = task.getLabels().stream().map(Label::getId).sorted().toList();
        return new TaskView(
                task.getId(),
                task.getTaskKey(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getAuthorId(),
                task.getAssigneeId(),
                task.getProject().getId(),
                labelIds,
                task.getVersion(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }

    public String normalizeTaskKey(String taskKey) {
        return taskKey.trim().toUpperCase();
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
