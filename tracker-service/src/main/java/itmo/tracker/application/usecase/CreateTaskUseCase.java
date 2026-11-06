package itmo.tracker.application.usecase;

import itmo.tracker.application.exception.ConflictException;
import itmo.tracker.application.mapper.TaskMapper;
import itmo.tracker.application.model.Actor;
import itmo.tracker.application.model.CreateTaskCommand;
import itmo.tracker.application.model.TaskAssignment;
import itmo.tracker.application.model.TaskView;
import itmo.tracker.application.port.in.LabelService;
import itmo.tracker.application.port.in.ProjectMemberService;
import itmo.tracker.application.port.in.ProjectService;
import itmo.tracker.application.port.out.TaskAssignments;
import itmo.tracker.application.port.out.TaskStore;
import itmo.tracker.application.port.out.UnitOfWork;
import itmo.tracker.application.port.out.UserDirectoryPort;
import itmo.tracker.application.service.TrackerAccess;
import itmo.tracker.domain.Label;
import itmo.tracker.domain.Project;
import itmo.tracker.domain.Task;

import java.util.Objects;
import java.util.Set;

public class CreateTaskUseCase {
    private final TrackerAccess access;
    private final TaskStore taskRepository;
    private final TaskMapper taskMapper;
    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;
    private final UserDirectoryPort userDirectory;
    private final UnitOfWork transactions;
    private final LabelService labelService;
    private final TaskAssignments assignments;

    public CreateTaskUseCase(
            TrackerAccess access,
            TaskStore taskRepository,
            TaskMapper taskMapper,
            ProjectService projectService,
            ProjectMemberService projectMemberService,
            UserDirectoryPort userDirectory,
            UnitOfWork transactions,
            LabelService labelService,
            TaskAssignments assignments) {
        this.access = access;
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
        this.projectService = projectService;
        this.projectMemberService = projectMemberService;
        this.userDirectory = userDirectory;
        this.transactions = transactions;
        this.labelService = labelService;
        this.assignments = assignments;
    }

    public TaskView execute(CreateTaskCommand request, Actor actor) {
        access.requireManager(actor);
        userDirectory.requireUsers(actor.id(), request.assigneeId());
        return transactions.write(
                () -> {
                    access.requireProjectManager(request.projectId(), actor);
                    String normalizedKey = taskMapper.normalizeTaskKey(request.taskKey());
                    ensureTaskKeyAvailable(normalizedKey);

                    Project project = resolveProject(request.projectId());
                    Long author = actor.id();
                    Long assignee = request.assigneeId();
                    requireAssigneeMember(request.projectId(), assignee);
                    Set<Label> labels = resolveLabels(request.labelIds());

                    Task task = taskMapper.toDomain(request, author, assignee, project, labels);
                    Task saved = taskRepository.save(task);
                    appendAssignment(saved, null, actor);
                    return taskMapper.toResponse(saved);
                });
    }

    private void appendAssignment(Task task, Long previousAssignee, Actor actor) {
        Long recipient = task.getAssigneeId();
        if (recipient != null
                && !Objects.equals(recipient, previousAssignee)
                && recipient != actor.id()) {
            assignments.append(
                    new TaskAssignment(
                            task.getId(),
                            task.getVersion(),
                            task.getTaskKey(),
                            task.getProject().getId(),
                            actor.id(),
                            recipient));
        }
    }

    private void requireAssigneeMember(Long projectId, Long assigneeId) {
        if (assigneeId != null && !projectMemberService.isActiveMember(projectId, assigneeId)) {
            throw new ConflictException(
                    "User with id '%d' is not an active member of project '%d'"
                            .formatted(assigneeId, projectId));
        }
    }

    private Project resolveProject(Long projectId) {
        return projectService.requireEntity(projectId);
    }

    private Set<Label> resolveLabels(Set<Long> labelIds) {
        return labelService.requireEntities(labelIds);
    }

    private void ensureTaskKeyAvailable(String taskKey) {
        if (taskRepository.existsByTaskKey(taskKey)) {
            throw taskKeyConflict(taskKey);
        }
    }

    private ConflictException taskKeyConflict(String taskKey) {
        return new ConflictException("Task with key '%s' already exists".formatted(taskKey));
    }
}
