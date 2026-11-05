package itmo.tracker.application.service;

import itmo.tracker.application.exception.ConflictException;
import itmo.tracker.application.exception.ResourceNotFoundException;
import itmo.tracker.application.mapper.TaskMapper;
import itmo.tracker.application.model.Actor;
import itmo.tracker.application.model.ChangeTaskStatusCommand;
import itmo.tracker.application.model.CreateTaskCommand;
import itmo.tracker.application.model.MoveTaskCommand;
import itmo.tracker.application.model.PageQuery;
import itmo.tracker.application.model.ResultPage;
import itmo.tracker.application.model.ResultSlice;
import itmo.tracker.application.model.TaskAssignment;
import itmo.tracker.application.model.TaskFeedView;
import itmo.tracker.application.model.TaskFilter;
import itmo.tracker.application.model.TaskStatusChange;
import itmo.tracker.application.model.TaskView;
import itmo.tracker.application.model.UpdateTaskCommand;
import itmo.tracker.application.port.in.*;
import itmo.tracker.application.port.in.LabelService;
import itmo.tracker.application.port.in.ProjectMemberService;
import itmo.tracker.application.port.in.ProjectService;
import itmo.tracker.application.port.out.TaskAssignments;
import itmo.tracker.application.port.out.TaskStatusChanges;
import itmo.tracker.application.port.out.TaskStore;
import itmo.tracker.application.port.out.UnitOfWork;
import itmo.tracker.application.port.out.UserDirectoryPort;
import itmo.tracker.domain.Label;
import itmo.tracker.domain.Project;
import itmo.tracker.domain.Task;
import itmo.tracker.domain.TaskStatus;

import java.util.Objects;
import java.util.Set;

public class TaskServiceImpl implements TaskService {
    public TaskServiceImpl(
            TrackerAccess access,
            TaskStore taskRepository,
            TaskMapper taskMapper,
            ProjectService projectService,
            ProjectMemberService projectMemberService,
            UserDirectoryPort userDirectory,
            UnitOfWork transactions,
            LabelService labelService,
            TaskAssignments assignments,
            TaskStatusChanges statusChanges) {
        this.access = access;
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
        this.projectService = projectService;
        this.projectMemberService = projectMemberService;
        this.userDirectory = userDirectory;
        this.transactions = transactions;
        this.labelService = labelService;
        this.assignments = assignments;
        this.statusChanges = statusChanges;
    }

    private final TrackerAccess access;

    private final TaskStore taskRepository;
    private final TaskMapper taskMapper;
    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;
    private final UserDirectoryPort userDirectory;
    private final UnitOfWork transactions;
    private final LabelService labelService;
    private final TaskAssignments assignments;
    private final TaskStatusChanges statusChanges;

    @Override
    public TaskView create(CreateTaskCommand request, Actor actor) {
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

    @Override
    public TaskView findById(Long id) {
        return transactions.read(
                () -> {
                    return taskMapper.toResponse(findEntity(id));
                });
    }

    @Override
    public ResultPage<TaskView> findAll(TaskFilter filter, PageQuery pageable) {
        return transactions.read(
                () -> {
                    return taskRepository
                            .findAllFiltered(
                                    filter.projectId(),
                                    filter.authorId(),
                                    filter.assigneeId(),
                                    filter.labelId(),
                                    filter.status(),
                                    filter.priority(),
                                    pageable)
                            .map(taskMapper::toResponse);
                });
    }

    @Override
    public TaskFeedView findFeed(Long afterId, int limit) {
        return transactions.read(
                () -> {
                    PageQuery pageable = PageQuery.of(0, limit);
                    ResultSlice<Task> slice =
                            afterId == null
                                    ? taskRepository.findAllByOrderByIdAsc(pageable)
                                    : taskRepository.findByIdGreaterThanOrderByIdAsc(
                                            afterId, pageable);
                    var tasks = slice.getContent().stream().map(taskMapper::toResponse).toList();
                    Long nextCursor =
                            slice.hasNext() && !tasks.isEmpty() ? tasks.getLast().id() : null;
                    return new TaskFeedView(tasks, nextCursor);
                });
    }

    @Override
    public TaskView move(Long id, MoveTaskCommand request, Actor actor) {
        access.requireManager(actor);
        userDirectory.requireUsers(request.assigneeId());
        return transactions.write(
                () -> {
                    Task task = findEntity(id);
                    access.requireProjectManager(task.getProject().getId(), actor);
                    access.requireProjectManager(request.projectId(), actor);
                    if (!Objects.equals(task.getVersion(), request.version())) {
                        throw versionConflict(id);
                    }
                    Project project = resolveProject(request.projectId());
                    Long assignee = request.assigneeId();
                    requireAssigneeMember(request.projectId(), assignee);
                    Set<Label> labels = resolveLabels(request.labelIds());
                    Long previousAssignee = task.getAssigneeId();
                    task.move(project, assignee, labels);

                    Task saved = taskRepository.save(task);
                    appendAssignment(saved, previousAssignee, actor);
                    return taskMapper.toResponse(saved);
                });
    }

    @Override
    public TaskView update(Long id, UpdateTaskCommand request, Actor actor) {
        access.requireManager(actor);
        TaskView current =
                transactions.write(
                        () -> {
                            Task task = findEntity(id);
                            access.requireProjectManager(task.getProject().getId(), actor);
                            return taskMapper.toResponse(task);
                        });
        if (!Objects.equals(current.version(), request.version())) {
            throw versionConflict(id);
        }
        userDirectory.requireUsers(
                Objects.equals(current.assigneeId(), request.assigneeId())
                        ? null
                        : request.assigneeId());
        return transactions.write(
                () -> {
                    Task task = findEntity(id);
                    access.requireProjectManager(task.getProject().getId(), actor);
                    if (!Objects.equals(task.getVersion(), request.version())) {
                        throw versionConflict(id);
                    }
                    if (!Objects.equals(task.getProject().getId(), request.projectId())) {
                        throw new ConflictException(
                                "Use the task move operation to change a task project");
                    }

                    String normalizedKey = taskMapper.normalizeTaskKey(request.taskKey());
                    if (taskRepository.existsByTaskKeyAndIdNot(normalizedKey, id)) {
                        throw taskKeyConflict(normalizedKey);
                    }

                    Project project = resolveProject(request.projectId());
                    Long author = task.getAuthorId();
                    Long assignee = request.assigneeId();
                    requireAssigneeMember(request.projectId(), assignee);
                    Set<Label> labels = resolveLabels(request.labelIds());
                    Long previousAssignee = task.getAssigneeId();
                    TaskStatus previousStatus = task.getStatus();
                    taskMapper.updateDomain(task, request, author, assignee, project, labels);

                    Task saved = taskRepository.save(task);
                    appendAssignment(saved, previousAssignee, actor);
                    appendStatusChange(saved, previousStatus, actor);
                    return taskMapper.toResponse(saved);
                });
    }

    @Override
    public void delete(Long id, Actor actor) {
        transactions.write(
                () -> {
                    access.requireManager(actor);
                    Task task = findEntity(id);
                    access.requireProjectManager(task.getProject().getId(), actor);
                    taskRepository.delete(task);

                    return null;
                });
    }

    @Override
    public TaskView changeStatus(Long id, ChangeTaskStatusCommand request, Actor actor) {
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

    private Task findEntity(Long id) {
        return taskRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
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

    private ConflictException versionConflict(Long taskId) {
        return new ConflictException(
                "Task with id '%d' was modified by another request".formatted(taskId));
    }
}
