package itmo.task.service;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.label.entity.Label;
import itmo.label.service.LabelService;
import itmo.project.entity.Project;
import itmo.project.entity.ProjectStatus;
import itmo.project.service.ProjectMemberService;
import itmo.project.service.ProjectService;
import itmo.task.dto.CreateTaskRequest;
import itmo.task.dto.MoveTaskRequest;
import itmo.task.dto.TaskFilter;
import itmo.task.dto.TaskResponse;
import itmo.task.dto.UpdateTaskRequest;
import itmo.task.entity.Task;
import itmo.task.entity.TaskPriority;
import itmo.task.entity.TaskStatus;
import itmo.task.mapper.TaskMapper;
import itmo.task.repository.TaskRepository;
import itmo.user.entity.User;
import itmo.user.entity.UserRole;
import itmo.user.service.UserService;
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
    private ProjectMemberService projectMemberService;

    @Mock
    private UserService userService;

    @Mock
    private LabelService labelService;

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
                projectMemberService,
                userService,
                labelService
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
        CreateTaskRequest request = createRequest();

        assertThatThrownBy(() -> taskService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PLATFORM-1");
        verify(taskRepository, never()).saveAndFlush(any(Task.class));
    }

    @Test
    void shouldTranslateDatabaseConflictDuringCreate() {
        prepareRelations();
        when(taskRepository.existsByTaskKey("PLATFORM-1")).thenReturn(false);
        when(taskRepository.saveAndFlush(any(Task.class)))
                .thenThrow(new DataIntegrityViolationException("uq_tasks_task_key"));
        CreateTaskRequest request = createRequest();

        assertThatThrownBy(() -> taskService.create(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldPropagateMissingRelatedResource() {
        when(taskRepository.existsByTaskKey("PLATFORM-1")).thenReturn(false);
        when(projectService.requireEntity(1L)).thenThrow(new ResourceNotFoundException("Project", 1L));
        CreateTaskRequest request = createRequest();

        assertThatThrownBy(() -> taskService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).saveAndFlush(any(Task.class));
    }

    @Test
    void shouldFindAndListTasks() {
        Task task = task();
        PageRequest pageable = PageRequest.of(0, 20);
        TaskFilter filter = new TaskFilter(1L, 2L, 3L, 4L, TaskStatus.TODO, TaskPriority.HIGH);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(taskRepository.findAllFiltered(
                1L, 2L, 3L, 4L, TaskStatus.TODO, TaskPriority.HIGH, pageable
        )).thenReturn(new PageImpl<>(List.of(task), pageable, 1));

        assertThat(taskService.findById(10L).taskKey()).isEqualTo("PLATFORM-1");
        assertThat(taskService.findAll(filter, pageable).getTotalElements()).isEqualTo(1);
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

        assertThat(firstPage.tasks()).extracting(TaskResponse::id).containsExactly(10L);
        assertThat(firstPage.nextCursor()).isEqualTo(10L);
        assertThat(lastPage.tasks()).hasSize(1);
        assertThat(lastPage.nextCursor()).isNull();
    }

    @Test
    void shouldMoveTaskWhenAssigneeIsTargetProjectMember() {
        Task task = task();
        Project targetProject = new Project("Mobile", "MOBILE", null, ProjectStatus.ACTIVE);
        User assignee = new User("Developer", "developer@example.com", UserRole.DEVELOPER);
        ReflectionTestUtils.setField(targetProject, "id", 4L);
        ReflectionTestUtils.setField(assignee, "id", 5L);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(projectService.requireEntity(4L)).thenReturn(targetProject);
        when(userService.requireEntity(5L)).thenReturn(assignee);
        when(projectMemberService.isActiveMember(4L, 5L)).thenReturn(true);
        when(labelService.requireEntities(Set.of(3L))).thenReturn(Set.of(label));
        when(taskRepository.saveAndFlush(task)).thenReturn(task);

        var response = taskService.move(10L, new MoveTaskRequest(4L, 5L, Set.of(3L), 0L));

        assertThat(response.projectId()).isEqualTo(4L);
        assertThat(response.assigneeId()).isEqualTo(5L);
        assertThat(response.labelIds()).containsExactly(3L);
    }

    @Test
    void shouldRejectMoveWhenAssigneeIsNotTargetProjectMember() {
        Task task = task();
        Project targetProject = new Project("Mobile", "MOBILE", null, ProjectStatus.ACTIVE);
        User assignee = new User("Developer", "developer@example.com", UserRole.DEVELOPER);
        ReflectionTestUtils.setField(targetProject, "id", 4L);
        ReflectionTestUtils.setField(assignee, "id", 5L);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(projectService.requireEntity(4L)).thenReturn(targetProject);
        when(userService.requireEntity(5L)).thenReturn(assignee);
        MoveTaskRequest request = new MoveTaskRequest(4L, 5L, Set.of(3L), 0L);

        assertThatThrownBy(() -> taskService.move(10L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not an active member");
        assertThat(task.getProject().getId()).isEqualTo(1L);
        verify(taskRepository, never()).saveAndFlush(task);
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
        UpdateTaskRequest request = updateRequest(7L);

        assertThatThrownBy(() -> taskService.update(10L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified by another request");
        verify(projectService, never()).requireEntity(any());
    }

    @Test
    void shouldRejectDuplicateTaskKeyDuringUpdate() {
        Task task = task();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(taskRepository.existsByTaskKeyAndIdNot("PLATFORM-2", 10L)).thenReturn(true);
        UpdateTaskRequest request = updateRequest(0L);

        assertThatThrownBy(() -> taskService.update(10L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PLATFORM-2");
    }

    @Test
    void shouldRejectProjectChangeThroughRegularUpdate() {
        Task task = task();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        UpdateTaskRequest request = new UpdateTaskRequest(
                "PLATFORM-2", "Updated", null, TaskStatus.IN_PROGRESS, TaskPriority.CRITICAL,
                2L, null, 4L, Set.of(3L), 0L
        );

        assertThatThrownBy(() -> taskService.update(10L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("move operation");
        verify(projectService, never()).requireEntity(any());
        verify(taskRepository, never()).saveAndFlush(task);
    }

    @Test
    void shouldTranslateConcurrentUpdateConflict() {
        Task task = task();
        prepareRelations();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(taskRepository.existsByTaskKeyAndIdNot("PLATFORM-2", 10L)).thenReturn(false);
        when(taskRepository.saveAndFlush(task))
                .thenThrow(new OptimisticLockingFailureException("stale"));
        UpdateTaskRequest request = updateRequest(0L);

        assertThatThrownBy(() -> taskService.update(10L, request))
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
        when(projectService.requireEntity(1L)).thenReturn(project);
        when(userService.requireEntity(2L)).thenReturn(author);
    }

    private void prepareRelations() {
        prepareProjectAndAuthor();
        when(labelService.requireEntities(Set.of(3L))).thenReturn(Set.of(label));
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

}
