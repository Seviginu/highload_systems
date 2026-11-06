package itmo.tracker.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import itmo.support.TestTransactions;
import itmo.tracker.application.exception.ConflictException;
import itmo.tracker.application.exception.ForbiddenException;
import itmo.tracker.application.exception.ResourceNotFoundException;
import itmo.tracker.application.mapper.TaskMapper;
import itmo.tracker.application.model.*;
import itmo.tracker.application.port.in.*;
import itmo.tracker.application.port.out.*;
import itmo.tracker.application.service.TrackerAccess;
import itmo.tracker.domain.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class TaskUseCasesTest {
    @Mock private TaskStore tasks;
    @Mock private ProjectMemberStore members;
    @Mock private TaskStatusChanges statusChanges;
    @Mock private TaskAssignments assignments;
    @Mock private ProjectService projects;
    @Mock private ProjectMemberService projectMembers;
    @Mock private UserDirectoryPort users;
    @Mock private LabelService labels;

    private CreateTaskUseCase createTask;
    private ChangeTaskStatusUseCase changeStatus;
    private final Actor actor = new Actor(2L, Set.of("ADMIN"));
    private final Project project = new Project(1L, "Platform", "PLATFORM", null, ProjectStatus.ACTIVE, null, null);

    @BeforeEach
    void setUp() {
        var access = new TrackerAccess(members);
        var mapper = new TaskMapper();
        var transactions = new TestTransactions();
        createTask = new CreateTaskUseCase(
                access, tasks, mapper, projects, projectMembers, users,
                transactions, labels, assignments);
        changeStatus = new ChangeTaskStatusUseCase(
                access, tasks, mapper, transactions, statusChanges);
    }

    @Test
    void creationRecordsAssignmentForAnotherUser() {
        when(projects.requireEntity(1L)).thenReturn(project);
        when(projectMembers.isActiveMember(1L, 3L)).thenReturn(true);
        when(labels.requireEntities(Set.of())).thenReturn(Set.of());
        when(tasks.save(any(Task.class))).thenReturn(task(2L, 3L));

        var result = createTask.execute(createCommand(), actor);

        assertThat(result.id()).isEqualTo(10L);
        verify(users).requireUsers(2L, 3L);
        verify(assignments).append(new TaskAssignment(10L, 4L, "PLATFORM-1", 1L, 2L, 3L));
    }

    @Test
    void creationRejectsInactiveAssigneeBeforeSaving() {
        when(projects.requireEntity(1L)).thenReturn(project);

        assertThatThrownBy(() -> createTask.execute(createCommand(), actor))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not an active member");

        verify(tasks, never()).save(any());
        verifyNoInteractions(assignments);
    }

    @Test
    void statusChangeRecordsEventForTaskAuthor() {
        var task = task(1L, 2L);
        when(tasks.findById(10L)).thenReturn(Optional.of(task));
        when(tasks.saveStatus(task)).thenReturn(task);

        var result = changeStatus.execute(10L, statusCommand(4L), actor);

        assertThat(result.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        verify(statusChanges).append(new TaskStatusChange(
                10L, 4L, "PLATFORM-1", 2L, 1L, "TODO", "IN_PROGRESS"));
    }

    @Test
    void statusChangeDoesNotNotifyActorAboutTheirOwnTask() {
        var task = task(2L, 3L);
        when(tasks.findById(10L)).thenReturn(Optional.of(task));
        when(tasks.saveStatus(task)).thenReturn(task);

        changeStatus.execute(10L, statusCommand(4L), actor);

        verify(tasks).saveStatus(task);
        verifyNoInteractions(statusChanges);
    }

    @Test
    void statusChangeRejectsStaleVersionWithoutSavingOrPublishing() {
        when(tasks.findById(10L)).thenReturn(Optional.of(task(1L, 2L)));

        assertThatThrownBy(() -> changeStatus.execute(10L, statusCommand(3L), actor))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified by another request");

        verify(tasks, never()).saveStatus(any());
        verifyNoInteractions(statusChanges);
    }

    @Test
    void statusChangeRejectsDeveloperWhoIsNotAssigned() {
        when(tasks.findById(10L)).thenReturn(Optional.of(task(1L, 3L)));
        var developer = new Actor(2L, Set.of("DEVELOPER"));

        assertThatThrownBy(() -> changeStatus.execute(10L, statusCommand(4L), developer))
                .isInstanceOf(ForbiddenException.class);

        verify(tasks, never()).saveStatus(any());
        verifyNoInteractions(statusChanges);
    }

    @Test
    void statusChangeReportsMissingTask() {
        assertThatThrownBy(() -> changeStatus.execute(10L, statusCommand(4L), actor))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tasks, never()).saveStatus(any());
        verifyNoInteractions(statusChanges);
    }

    private CreateTaskCommand createCommand() {
        return new CreateTaskCommand(
                "PLATFORM-1", "Task", null, TaskStatus.TODO, TaskPriority.HIGH,
                3L, 1L, Set.of());
    }

    private ChangeTaskStatusCommand statusCommand(Long version) {
        return new ChangeTaskStatusCommand(TaskStatus.IN_PROGRESS, version);
    }

    private Task task(Long author, Long assignee) {
        return new Task(
                10L, "PLATFORM-1", "Task", null, TaskStatus.TODO, TaskPriority.HIGH,
                author, assignee, project, 4L, null, null, Set.of());
    }
}
