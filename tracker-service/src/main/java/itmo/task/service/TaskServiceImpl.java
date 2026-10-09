package itmo.task.service;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.label.entity.Label;
import itmo.label.service.LabelService;
import itmo.project.entity.Project;
import itmo.project.service.ProjectMemberService;
import itmo.project.service.ProjectService;
import itmo.task.dto.CreateTaskRequest;
import itmo.task.dto.MoveTaskRequest;
import itmo.task.dto.TaskFeedResponse;
import itmo.task.dto.TaskFilter;
import itmo.task.dto.TaskResponse;
import itmo.task.dto.UpdateTaskRequest;
import itmo.task.entity.Task;
import itmo.task.mapper.TaskMapper;
import itmo.task.repository.TaskRepository;
import itmo.integration.user.UserDirectory;
import org.springframework.transaction.support.TransactionOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

import static itmo.common.persistence.ConstraintViolationDetector.isViolationOf;
import static itmo.infrastructure.cache.TaskCacheConfiguration.TASKS_CACHE;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;
    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;
    private final UserDirectory userDirectory;
    private final TransactionOperations transactions;
    private final LabelService labelService;

    @Override
    @CachePut(cacheNames = TASKS_CACHE, key = "#result.id()")
    public TaskResponse create(CreateTaskRequest request) {
        userDirectory.requireUsers(request.authorId(), request.assigneeId());
        return transactions.execute(transactionStatus -> {
            String normalizedKey = taskMapper.normalizeTaskKey(request.taskKey());
            ensureTaskKeyAvailable(normalizedKey);

            Project project = resolveProject(request.projectId());
            Long author = request.authorId();
            Long assignee = request.assigneeId();
            Set<Label> labels = resolveLabels(request.labelIds());

            try {
                Task task = taskMapper.toEntity(request, author, assignee, project, labels);
                return taskMapper.toResponse(taskRepository.saveAndFlush(task));
            } catch (DataIntegrityViolationException exception) {
                if (isViolationOf(exception, "uq_tasks_task_key")) {
                    throw taskKeyConflict(normalizedKey);
                }
                throw exception;
            }

        });
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = TASKS_CACHE, key = "#id", sync = true)
    public TaskResponse findById(Long id) {
        return taskMapper.toResponse(findEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskResponse> findAll(TaskFilter filter, Pageable pageable) {
        return taskRepository.findAllFiltered(
                filter.projectId(),
                filter.authorId(),
                filter.assigneeId(),
                filter.labelId(),
                filter.status(),
                filter.priority(),
                pageable
        ).map(taskMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskFeedResponse findFeed(Long afterId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        Slice<Task> slice = afterId == null
                ? taskRepository.findAllByOrderByIdAsc(pageable)
                : taskRepository.findByIdGreaterThanOrderByIdAsc(afterId, pageable);
        var tasks = slice.getContent().stream()
                .map(taskMapper::toResponse)
                .toList();
        Long nextCursor = slice.hasNext() && !tasks.isEmpty()
                ? tasks.getLast().id()
                : null;
        return new TaskFeedResponse(tasks, nextCursor);
    }

    @Override
    @CachePut(cacheNames = TASKS_CACHE, key = "#id")
    public TaskResponse move(Long id, MoveTaskRequest request) {
        userDirectory.requireUsers(request.assigneeId());
        return transactions.execute(transactionStatus -> {
            Task task = findEntity(id);
            if (!Objects.equals(task.getVersion(), request.version())) {
                throw versionConflict(id);
            }
            Project project = resolveProject(request.projectId());
            Long assignee = request.assigneeId();
            if (assignee != null && !projectMemberService.isActiveMember(request.projectId(), request.assigneeId())) {
                throw new ConflictException(
                        "User with id '%d' is not an active member of project '%d'"
                                .formatted(request.assigneeId(), request.projectId())
                );
            }
            Set<Label> labels = resolveLabels(request.labelIds());
            task.move(project, assignee, labels);

            try {
                return taskMapper.toResponse(taskRepository.saveAndFlush(task));
            } catch (OptimisticLockingFailureException exception) {
                throw versionConflict(id);
            }

        });
    }

    @Override
    @CachePut(cacheNames = TASKS_CACHE, key = "#id")
    public TaskResponse update(Long id, UpdateTaskRequest request) {
        TaskResponse current = transactions.execute(status -> taskMapper.toResponse(findEntity(id)));
        if (!Objects.equals(current.version(), request.version())) {
            throw versionConflict(id);
        }
        userDirectory.requireUsers(
                Objects.equals(current.authorId(), request.authorId()) ? null : request.authorId(),
                Objects.equals(current.assigneeId(), request.assigneeId()) ? null : request.assigneeId());
        return transactions.execute(transactionStatus -> {
            Task task = findEntity(id);
            if (!Objects.equals(task.getVersion(), request.version())) {
                throw versionConflict(id);
            }
            if (!Objects.equals(task.getProject().getId(), request.projectId())) {
                throw new ConflictException("Use the task move operation to change a task project");
            }

            String normalizedKey = taskMapper.normalizeTaskKey(request.taskKey());
            if (taskRepository.existsByTaskKeyAndIdNot(normalizedKey, id)) {
                throw taskKeyConflict(normalizedKey);
            }

            Project project = resolveProject(request.projectId());
            Long author = request.authorId();
            Long assignee = request.assigneeId();
            Set<Label> labels = resolveLabels(request.labelIds());
            taskMapper.updateEntity(task, request, author, assignee, project, labels);

            try {
                return taskMapper.toResponse(taskRepository.saveAndFlush(task));
            } catch (OptimisticLockingFailureException exception) {
                throw versionConflict(id);
            } catch (DataIntegrityViolationException exception) {
                if (isViolationOf(exception, "uq_tasks_task_key")) {
                    throw taskKeyConflict(normalizedKey);
                }
                throw exception;
            }

        });
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = TASKS_CACHE, key = "#id")
    public void delete(Long id) {
        Task task = findEntity(id);
        taskRepository.delete(task);
        taskRepository.flush();
    }

    private Task findEntity(Long id) {
        return taskRepository.findById(id)
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
        return new ConflictException("Task with id '%d' was modified by another request".formatted(taskId));
    }
}
