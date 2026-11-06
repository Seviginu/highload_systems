package itmo.tracker.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import itmo.support.TestTransactions;
import itmo.support.TestUser;
import itmo.tracker.application.exception.ConflictException;
import itmo.tracker.application.exception.ResourceNotFoundException;
import itmo.tracker.application.mapper.TaskMapper;
import itmo.tracker.application.model.CreateTaskCommand;
import itmo.tracker.application.model.MoveTaskCommand;
import itmo.tracker.application.model.PageQuery;
import itmo.tracker.application.model.ResultPage;
import itmo.tracker.application.model.ResultSlice;
import itmo.tracker.application.model.TaskFilter;
import itmo.tracker.application.model.TaskView;
import itmo.tracker.application.model.UpdateTaskCommand;
import itmo.tracker.application.port.in.LabelService;
import itmo.tracker.application.port.in.ProjectMemberService;
import itmo.tracker.application.port.in.ProjectService;
import itmo.tracker.application.port.out.TaskStore;
import itmo.tracker.application.port.out.TaskAssignments;
import itmo.tracker.application.port.out.TaskStatusChanges;
import itmo.tracker.application.usecase.CreateTaskUseCase;
import itmo.tracker.application.usecase.ChangeTaskStatusUseCase;
import itmo.tracker.application.port.out.UserDirectoryPort;
import itmo.tracker.domain.Label;
import itmo.tracker.domain.Project;
import itmo.tracker.domain.ProjectStatus;
import itmo.tracker.domain.Task;
import itmo.tracker.domain.TaskPriority;
import itmo.tracker.domain.TaskStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock private TaskStore taskRepository;

    @Mock private ProjectService projectService;

    @Mock private ProjectMemberService projectMemberService;

    @Mock private UserDirectoryPort userDirectory;

    @Mock private LabelService labelService;

    private TaskServiceImpl taskService;
    private Project project;
    private TestUser author;
    private Label label;

    @BeforeEach
    void setUp() {
        var access = itmo.support.TestActors.access();
        var mapper = new TaskMapper();
        var transactions = new TestTransactions();
        var assignments = org.mockito.Mockito.mock(TaskAssignments.class);
        var statusChanges = org.mockito.Mockito.mock(TaskStatusChanges.class);
        taskService =
                new TaskServiceImpl(
                        access,
                        taskRepository,
                        mapper,
                        projectService,
                        projectMemberService,
                        userDirectory,
                        transactions,
                        labelService,
                        assignments,
                        statusChanges,
                        new CreateTaskUseCase(
                                access, taskRepository, mapper, projectService,
                                projectMemberService, userDirectory, transactions,
                                labelService, assignments),
                        new ChangeTaskStatusUseCase(
                                access, taskRepository, mapper, transactions, statusChanges));
        project = new Project("Platform", "PLATFORM", null, ProjectStatus.ACTIVE);
        author = new TestUser("TEAM_LEAD");
        label = new Label("Backend", "#112233");
        ReflectionTestUtils.setField(project, "id", 1L);
        ReflectionTestUtils.setField(author, "id", 2L);
        ReflectionTestUtils.setField(label, "id", 3L);
    }

    @Test
    void shouldCreateTaskWithResolvedRelations() {
        prepareRelations();
        when(taskRepository.existsByTaskKey("PLATFORM-1")).thenReturn(false);
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        var response = taskService.create(createRequest(), itmo.support.TestActors.admin());

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
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));
        CreateTaskCommand request =
                new CreateTaskCommand(
                        "PLATFORM-1",
                        "Task",
                        null,
                        TaskStatus.TODO,
                        TaskPriority.MEDIUM,
                        null,
                        1L,
                        null);

        assertThat(taskService.create(request, itmo.support.TestActors.admin()).labelIds())
                .isEmpty();
    }

    @Test
    void shouldRejectDuplicateTaskKey() {
        when(taskRepository.existsByTaskKey("PLATFORM-1")).thenReturn(true);
        CreateTaskCommand request = createRequest();

        assertThatThrownBy(() -> taskService.create(request, itmo.support.TestActors.admin()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PLATFORM-1");
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void shouldPropagateMissingRelatedResource() {
        when(taskRepository.existsByTaskKey("PLATFORM-1")).thenReturn(false);
        when(projectService.requireEntity(1L))
                .thenThrow(new ResourceNotFoundException("Project", 1L));
        CreateTaskCommand request = createRequest();

        assertThatThrownBy(() -> taskService.create(request, itmo.support.TestActors.admin()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void shouldFindAndListTasks() {
        Task task = task();
        PageQuery pageable = PageQuery.of(0, 20);
        TaskFilter filter = new TaskFilter(1L, 2L, 3L, 4L, TaskStatus.TODO, TaskPriority.HIGH);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(taskRepository.findAllFiltered(
                        1L, 2L, 3L, 4L, TaskStatus.TODO, TaskPriority.HIGH, pageable))
                .thenReturn(new ResultPage<>(List.of(task), 1));

        assertThat(taskService.findById(10L).taskKey()).isEqualTo("PLATFORM-1");
        assertThat(taskService.findAll(filter, pageable).getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldReadCursorFeedWithAndWithoutCursor() {
        Task task = task();
        PageQuery pageable = PageQuery.of(0, 2);
        when(taskRepository.findAllByOrderByIdAsc(pageable))
                .thenReturn(new ResultSlice<>(List.of(task), true));
        when(taskRepository.findByIdGreaterThanOrderByIdAsc(10L, pageable))
                .thenReturn(new ResultSlice<>(List.of(task), false));

        var firstPage = taskService.findFeed(null, 2);
        var lastPage = taskService.findFeed(10L, 2);

        assertThat(firstPage.tasks()).extracting(TaskView::id).containsExactly(10L);
        assertThat(firstPage.nextCursor()).isEqualTo(10L);
        assertThat(lastPage.tasks()).hasSize(1);
        assertThat(lastPage.nextCursor()).isNull();
    }

    @Test
    void shouldMoveTaskWhenAssigneeIsTargetProjectMember() {
        Task task = task();
        Project targetProject = new Project("Mobile", "MOBILE", null, ProjectStatus.ACTIVE);
        TestUser assignee = new TestUser("DEVELOPER");
        ReflectionTestUtils.setField(targetProject, "id", 4L);
        ReflectionTestUtils.setField(assignee, "id", 5L);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(projectService.requireEntity(4L)).thenReturn(targetProject);
        when(projectMemberService.isActiveMember(4L, 5L)).thenReturn(true);
        when(labelService.requireEntities(Set.of(3L))).thenReturn(Set.of(label));
        when(taskRepository.save(task)).thenReturn(task);

        var response =
                taskService.move(
                        10L,
                        new MoveTaskCommand(4L, 5L, Set.of(3L), 0L),
                        itmo.support.TestActors.admin());

        assertThat(response.projectId()).isEqualTo(4L);
        assertThat(response.assigneeId()).isEqualTo(5L);
        assertThat(response.labelIds()).containsExactly(3L);
    }

    @Test
    void shouldRejectMoveWhenAssigneeIsNotTargetProjectMember() {
        Task task = task();
        Project targetProject = new Project("Mobile", "MOBILE", null, ProjectStatus.ACTIVE);
        TestUser assignee = new TestUser("DEVELOPER");
        ReflectionTestUtils.setField(targetProject, "id", 4L);
        ReflectionTestUtils.setField(assignee, "id", 5L);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(projectService.requireEntity(4L)).thenReturn(targetProject);
        MoveTaskCommand request = new MoveTaskCommand(4L, 5L, Set.of(3L), 0L);

        assertThatThrownBy(() -> taskService.move(10L, request, itmo.support.TestActors.admin()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not an active member");
        assertThat(task.getProject().getId()).isEqualTo(1L);
        verify(taskRepository, never()).save(task);
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
        when(taskRepository.save(task)).thenReturn(task);

        var response = taskService.update(10L, updateRequest(0L), itmo.support.TestActors.admin());

        assertThat(response.taskKey()).isEqualTo("PLATFORM-2");
        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void shouldRejectStaleVersionBeforeChangingRelations() {
        Task task = task();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        UpdateTaskCommand request = updateRequest(7L);

        assertThatThrownBy(() -> taskService.update(10L, request, itmo.support.TestActors.admin()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified by another request");
        verify(projectService, never()).requireEntity(any());
    }

    @Test
    void shouldRejectDuplicateTaskKeyDuringUpdate() {
        Task task = task();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(taskRepository.existsByTaskKeyAndIdNot("PLATFORM-2", 10L)).thenReturn(true);
        UpdateTaskCommand request = updateRequest(0L);

        assertThatThrownBy(() -> taskService.update(10L, request, itmo.support.TestActors.admin()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PLATFORM-2");
    }

    @Test
    void shouldRejectProjectChangeThroughRegularUpdate() {
        Task task = task();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        UpdateTaskCommand request =
                new UpdateTaskCommand(
                        "PLATFORM-2",
                        "Updated",
                        null,
                        TaskStatus.IN_PROGRESS,
                        TaskPriority.CRITICAL,
                        null,
                        4L,
                        Set.of(3L),
                        0L);

        assertThatThrownBy(() -> taskService.update(10L, request, itmo.support.TestActors.admin()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("move operation");
        verify(projectService, never()).requireEntity(any());
        verify(taskRepository, never()).save(task);
    }

    @Test
    void shouldDeleteTask() {
        Task task = task();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        taskService.delete(10L, itmo.support.TestActors.admin());

        verify(taskRepository).delete(task);
    }

    private CreateTaskCommand createRequest() {
        return new CreateTaskCommand(
                " platform-1 ",
                "Task",
                "Description",
                TaskStatus.TODO,
                TaskPriority.HIGH,
                null,
                1L,
                Set.of(3L));
    }

    private UpdateTaskCommand updateRequest(Long version) {
        return new UpdateTaskCommand(
                "platform-2",
                "Updated",
                null,
                TaskStatus.IN_PROGRESS,
                TaskPriority.CRITICAL,
                null,
                1L,
                Set.of(3L),
                version);
    }

    private void prepareProjectAndAuthor() {
        when(projectService.requireEntity(1L)).thenReturn(project);
    }

    private void prepareRelations() {
        prepareProjectAndAuthor();
        when(labelService.requireEntities(Set.of(3L))).thenReturn(Set.of(label));
    }

    private Task task() {
        Task task =
                new Task(
                        "PLATFORM-1",
                        "Task",
                        null,
                        TaskStatus.TODO,
                        TaskPriority.HIGH,
                        author.getId(),
                        null,
                        project,
                        Set.of(label));
        return persisted(task);
    }

    private Task persisted(Task task) {
        ReflectionTestUtils.setField(task, "id", 10L);
        ReflectionTestUtils.setField(task, "version", 0L);
        return task;
    }
}
