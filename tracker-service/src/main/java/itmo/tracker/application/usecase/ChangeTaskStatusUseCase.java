package itmo.tracker.application.usecase;

import itmo.tracker.application.exception.ConflictException;
import itmo.tracker.application.exception.ResourceNotFoundException;
import itmo.tracker.application.mapper.TaskMapper;
import itmo.tracker.application.model.Actor;
import itmo.tracker.application.model.ChangeTaskStatusCommand;
import itmo.tracker.application.model.TaskStatusChange;
import itmo.tracker.application.model.TaskView;
import itmo.tracker.application.port.out.TaskStatusChanges;
import itmo.tracker.application.port.out.TaskStore;
import itmo.tracker.application.port.out.UnitOfWork;
import itmo.tracker.application.service.TrackerAccess;
import itmo.tracker.domain.Task;
import itmo.tracker.domain.TaskStatus;

import java.util.Objects;

public class ChangeTaskStatusUseCase {
    private final TrackerAccess access;
    private final TaskStore taskRepository;
    private final TaskMapper taskMapper;
    private final UnitOfWork transactions;
    private final TaskStatusChanges statusChanges;

    public ChangeTaskStatusUseCase(
            TrackerAccess access,
            TaskStore taskRepository,
            TaskMapper taskMapper,
            UnitOfWork transactions,
            TaskStatusChanges statusChanges) {
        this.access = access;
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
        this.transactions = transactions;
        this.statusChanges = statusChanges;
    }

    public TaskView execute(Long id, ChangeTaskStatusCommand request, Actor actor) {
        return transactions.write(
                () -> {
                    Task task = findEntity(id);
                    access.requireStatusChange(task, actor);
                    if (!Objects.equals(task.getVersion(), request.version())) {
                        throw versionConflict(id);
                    }
                    TaskStatus previousStatus = task.getStatus();
                    task.changeStatus(request.status());

                    Task saved = taskRepository.saveStatus(task);
                    appendStatusChange(saved, previousStatus, actor);
                    return taskMapper.toResponse(saved);
                });
    }

    private void appendStatusChange(Task task, TaskStatus previousStatus, Actor actor) {
        if (task.getStatus() != previousStatus && task.getAuthorId() != actor.id()) {
            statusChanges.append(
                    new TaskStatusChange(
                            task.getId(),
                            task.getVersion(),
                            task.getTaskKey(),
                            actor.id(),
                            task.getAuthorId(),
                            previousStatus.name(),
                            task.getStatus().name()));
        }
    }

    private Task findEntity(Long id) {
        return taskRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    private ConflictException versionConflict(Long taskId) {
        return new ConflictException(
                "Task with id '%d' was modified by another request".formatted(taskId));
    }
}
