package itmo.task.service;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.label.entity.Label;
import itmo.label.service.LabelService;
import itmo.project.entity.Project;
import itmo.project.service.ProjectService;
import itmo.task.dto.CreateTaskRequest;
import itmo.task.dto.TaskResponse;
import itmo.task.dto.UpdateTaskRequest;
import itmo.task.entity.Task;
import itmo.task.mapper.TaskMapper;
import itmo.task.repository.TaskRepository;
import itmo.user.entity.User;
import itmo.user.service.UserService;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;
    private final ProjectService projectService;
    private final UserService userService;
    private final LabelService labelService;
    private final EntityManager entityManager;

    public TaskServiceImpl(
            TaskRepository taskRepository,
            TaskMapper taskMapper,
            ProjectService projectService,
            UserService userService,
            LabelService labelService,
            EntityManager entityManager
    ) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
        this.projectService = projectService;
        this.userService = userService;
        this.labelService = labelService;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        String normalizedKey = taskMapper.normalizeTaskKey(request.taskKey());
        ensureTaskKeyAvailable(normalizedKey);

        Project project = resolveProject(request.projectId());
        User author = resolveUser(request.authorId());
        User assignee = resolveOptionalUser(request.assigneeId());
        Set<Label> labels = resolveLabels(request.labelIds());

        try {
            Task task = taskMapper.toEntity(request, author, assignee, project, labels);
            return taskMapper.toResponse(taskRepository.saveAndFlush(task));
        } catch (DataIntegrityViolationException exception) {
            throw taskKeyConflict(normalizedKey);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse findById(Long id) {
        return taskMapper.toResponse(findEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskResponse> findAll(Pageable pageable) {
        return taskRepository.findAll(pageable).map(taskMapper::toResponse);
    }

    @Override
    @Transactional
    public TaskResponse update(Long id, UpdateTaskRequest request) {
        Task task = findEntity(id);
        if (!Objects.equals(task.getVersion(), request.version())) {
            throw versionConflict(id);
        }

        String normalizedKey = taskMapper.normalizeTaskKey(request.taskKey());
        if (taskRepository.existsByTaskKeyAndIdNot(normalizedKey, id)) {
            throw taskKeyConflict(normalizedKey);
        }

        Project project = resolveProject(request.projectId());
        User author = resolveUser(request.authorId());
        User assignee = resolveOptionalUser(request.assigneeId());
        Set<Label> labels = resolveLabels(request.labelIds());
        taskMapper.updateEntity(task, request, author, assignee, project, labels);

        try {
            return taskMapper.toResponse(taskRepository.saveAndFlush(task));
        } catch (OptimisticLockingFailureException exception) {
            throw versionConflict(id);
        } catch (DataIntegrityViolationException exception) {
            throw taskKeyConflict(normalizedKey);
        }
    }

    @Override
    @Transactional
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
        projectService.findById(projectId);
        return entityManager.getReference(Project.class, projectId);
    }

    private User resolveUser(Long userId) {
        userService.findById(userId);
        return entityManager.getReference(User.class, userId);
    }

    private User resolveOptionalUser(Long userId) {
        return userId == null ? null : resolveUser(userId);
    }

    private Set<Label> resolveLabels(Set<Long> labelIds) {
        Set<Label> labels = new LinkedHashSet<>();
        if (labelIds == null) {
            return labels;
        }
        for (Long labelId : labelIds) {
            labelService.findById(labelId);
            labels.add(entityManager.getReference(Label.class, labelId));
        }
        return labels;
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
