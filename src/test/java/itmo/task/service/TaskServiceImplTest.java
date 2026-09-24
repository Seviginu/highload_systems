package itmo.task.service;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.label.dto.LabelResponse;
import itmo.label.entity.Label;
import itmo.label.service.LabelService;
import itmo.project.dto.ProjectResponse;
import itmo.project.entity.Project;
import itmo.project.entity.ProjectStatus;
import itmo.project.service.ProjectService;
import itmo.task.dto.CreateTaskRequest;
import itmo.task.dto.UpdateTaskRequest;
import itmo.task.entity.Task;
import itmo.task.entity.TaskPriority;
import itmo.task.entity.TaskStatus;
import itmo.task.mapper.TaskMapper;
import itmo.task.repository.TaskRepository;
import itmo.user.dto.UserResponse;
import itmo.user.entity.User;
import itmo.user.entity.UserRole;
import itmo.user.service.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private UserService userService;

    @Mock
    private LabelService labelService;

    @Mock
    private EntityManager entityManager;

    private TaskServiceImpl taskService;
    private Project project;
    private User author;
    private Label label;

    @BeforeEach
    void setUp() {
        taskService = new TaskServiceImpl(
                taskRepository,
                new TaskMapper(),
                projectService,
                userService,
                labelService,
                entityManager
        );
        project = new Project("Platform", "PLATFORM", null, ProjectStatus.ACTIVE);
        author = new User("Author", "author@example.com", UserRole.TEAM_LEAD);
        label = new Label("Backend", "#112233");
        ReflectionTestUtils.setField(project, "id", 1L);
        ReflectionTestUtils.setField(author, "id", 2L);
        ReflectionTestUtils.setField(label, "id", 3L);
    }

    @Test
    void shouldCreateTaskWithResolvedRelations() {
        prepareRelations();
        when(taskRepository.existsByTaskKey("PLATFORM-1")).thenReturn(false);
        when(taskRepository.saveAndFlush(any(Task.class))).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        var response = taskService.create(createRequest());

        assertThat(response.taskKey()).isEqualTo("PLATFORM-1");
        assertThat(response.projectId()).isEqualTo(1L);
        assertThat(response.authorId()).isEqualTo(2L);
        assertThat(response.assigneeId()).isNull();
        assertThat(response.labelIds()).containsExactly(3L);
        assertThat(response.version()).isZero();
    }

    @Test
    void shouldCreateTaskWithoutLabelsWhenIdsAreNull() {
        prepareProjectAndAuthor();
        when(taskRepository.existsByTaskKey("PLATFORM-1")).thenReturn(false);
        when(taskRepository.saveAndFlush(any(Task.class))).thenAnswer(invocation -> persisted(invocation.getArgument(0)));
        CreateTaskRequest request = new CreateTaskRequest(
                "PLATFORM-1", "Task", null, TaskStatus.TODO, TaskPriority.MEDIUM,
                2L, null, 1L, null
        );

        assertThat(taskService.create(request).labelIds()).isEmpty();
    }

    @Test
    void shouldRejectDuplicateTaskKey() {
        when(taskRepository.existsByTaskKey("PLATFORM-1")).thenReturn(true);

        assertThatThrownBy(() -> taskService.create(createRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PLATFORM-1");
        verify(taskRepository, never()).saveAndFlush(any(Task.class));
    }

    @Test
    void shouldTranslateDatabaseConflictDuringCreate() {
        prepareRelations();
        when(taskRepository.existsByTaskKey("PLATFORM-1")).thenReturn(false);
        when(taskRepository.saveAndFlush(any(Task.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> taskService.create(createRequest()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldPropagateMissingRelatedResource() {
        when(taskRepository.existsByTaskKey("PLATFORM-1")).thenReturn(false);
        when(projectService.findById(1L)).thenThrow(new ResourceNotFoundException("Project", 1L));

        assertThatThrownBy(() -> taskService.create(createRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).saveAndFlush(any(Task.class));
    }

    @Test
    void shouldFindAndListTasks() {
        Task task = task();
        PageRequest pageable = PageRequest.of(0, 20);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(taskRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(task), pageable, 1));

        assertThat(taskService.findById(10L).taskKey()).isEqualTo("PLATFORM-1");
        assertThat(taskService.findAll(pageable).getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldReadCursorFeedWithAndWithoutCursor() {
        Task task = task();
        PageRequest pageable = PageRequest.of(0, 2);
        when(taskRepository.findAllByOrderByIdAsc(pageable))
                .thenReturn(new SliceImpl<>(List.of(task), pageable, true));
        when(taskRepository.findByIdGreaterThanOrderByIdAsc(10L, pageable))
                .thenReturn(new SliceImpl<>(List.of(task), pageable, false));

        var firstPage = taskService.findFeed(null, 2);
        var lastPage = taskService.findFeed(10L, 2);

        assertThat(firstPage.tasks()).extracting(response -> response.id()).containsExactly(10L);
        assertThat(firstPage.nextCursor()).isEqualTo(10L);
        assertThat(lastPage.tasks()).hasSize(1);
        assertThat(lastPage.nextCursor()).isNull();
    }

    @Test
    void shouldReportMissingTask() {
        when(taskRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.findById(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void shouldUpdateTask() {
        Task task = task();
        prepareRelations();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(taskRepository.existsByTaskKeyAndIdNot("PLATFORM-2", 10L)).thenReturn(false);
        when(taskRepository.saveAndFlush(task)).thenReturn(task);

        var response = taskService.update(10L, updateRequest(0L));

        assertThat(response.taskKey()).isEqualTo("PLATFORM-2");
        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void shouldRejectStaleVersionBeforeChangingRelations() {
        Task task = task();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.update(10L, updateRequest(7L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified by another request");
        verify(projectService, never()).findById(any());
    }

    @Test
    void shouldRejectDuplicateTaskKeyDuringUpdate() {
        Task task = task();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(taskRepository.existsByTaskKeyAndIdNot("PLATFORM-2", 10L)).thenReturn(true);

        assertThatThrownBy(() -> taskService.update(10L, updateRequest(0L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PLATFORM-2");
    }

    @Test
    void shouldTranslateConcurrentUpdateConflict() {
        Task task = task();
        prepareRelations();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(taskRepository.existsByTaskKeyAndIdNot("PLATFORM-2", 10L)).thenReturn(false);
        when(taskRepository.saveAndFlush(task))
                .thenThrow(new OptimisticLockingFailureException("stale"));

        assertThatThrownBy(() -> taskService.update(10L, updateRequest(0L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified by another request");
    }

    @Test
    void shouldDeleteTask() {
        Task task = task();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        taskService.delete(10L);

        verify(taskRepository).delete(task);
        verify(taskRepository).flush();
    }

    private CreateTaskRequest createRequest() {
        return new CreateTaskRequest(
                " platform-1 ", "Task", "Description", TaskStatus.TODO, TaskPriority.HIGH,
                2L, null, 1L, Set.of(3L)
        );
    }

    private UpdateTaskRequest updateRequest(Long version) {
        return new UpdateTaskRequest(
                "platform-2", "Updated", null, TaskStatus.IN_PROGRESS, TaskPriority.CRITICAL,
                2L, null, 1L, Set.of(3L), version
        );
    }

    private void prepareProjectAndAuthor() {
        when(projectService.findById(1L)).thenReturn(projectResponse());
        when(userService.findById(2L)).thenReturn(userResponse());
        when(entityManager.getReference(Project.class, 1L)).thenReturn(project);
        when(entityManager.getReference(User.class, 2L)).thenReturn(author);
    }

    private void prepareRelations() {
        prepareProjectAndAuthor();
        when(labelService.findById(3L)).thenReturn(labelResponse());
        when(entityManager.getReference(Label.class, 3L)).thenReturn(label);
    }

    private Task task() {
        Task task = new Task(
                "PLATFORM-1", "Task", null, TaskStatus.TODO, TaskPriority.HIGH,
                author, null, project, Set.of(label)
        );
        return persisted(task);
    }

    private Task persisted(Task task) {
        ReflectionTestUtils.setField(task, "id", 10L);
        ReflectionTestUtils.setField(task, "version", 0L);
        return task;
    }

    private ProjectResponse projectResponse() {
        return new ProjectResponse(1L, "Platform", "PLATFORM", null, ProjectStatus.ACTIVE, Instant.now(), Instant.now());
    }

    private UserResponse userResponse() {
        return new UserResponse(2L, "Author", "author@example.com", UserRole.TEAM_LEAD, Instant.now(), Instant.now());
    }

    private LabelResponse labelResponse() {
        return new LabelResponse(3L, "Backend", "#112233", Instant.now());
    }
}
