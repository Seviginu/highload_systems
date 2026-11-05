package itmo.tracker.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import itmo.tracker.application.exception.ConflictException;
import itmo.tracker.domain.*;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

class JpaPersistenceErrorsTest {
    private final PersistenceMapper mapper = new PersistenceMapper();

    @Test
    void shouldTranslateProjectCodeConflictDuringCreateAndUpdate() {
        var repository = mock(ProjectRepository.class);
        var projects = new JpaProjects(repository, mapper);
        var project = new Project("Platform", "PLATFORM", null, ProjectStatus.ACTIVE);
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_projects_code"));
        assertThatThrownBy(() -> projects.save(project))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PLATFORM");
        ReflectionTestUtils.setField(project, "id", 1L);
        when(repository.findById(1L))
                .thenReturn(
                        Optional.of(new ProjectEntity("Old", "OLD", null, ProjectStatus.ACTIVE)));
        assertThatThrownBy(() -> projects.save(project))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PLATFORM");
    }

    @Test
    void shouldTranslateLabelNameConflictDuringCreateAndUpdate() {
        var repository = mock(LabelRepository.class);
        var labels = new JpaLabels(repository, mapper);
        var label = new Label("Bug", "#FF0000");
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_labels_name_lower"));
        assertThatThrownBy(() -> labels.save(label))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Bug");
        ReflectionTestUtils.setField(label, "id", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(new LabelEntity("Old", "#000000")));
        assertThatThrownBy(() -> labels.save(label))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Bug");
    }

    @Test
    void shouldTranslateProjectAndLabelReferencesOnDelete() {
        var projectRepository = mock(ProjectRepository.class);
        var labelRepository = mock(LabelRepository.class);
        doThrow(new DataIntegrityViolationException("fk_tasks_project"))
                .when(projectRepository)
                .flush();
        doThrow(new DataIntegrityViolationException("fk_task_labels_label"))
                .when(labelRepository)
                .flush();
        var project = new Project("Platform", "PLATFORM", null, ProjectStatus.ACTIVE);
        var label = new Label("Bug", "#FF0000");
        ReflectionTestUtils.setField(project, "id", 1L);
        ReflectionTestUtils.setField(label, "id", 1L);
        assertThatThrownBy(() -> new JpaProjects(projectRepository, mapper).delete(project))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("referenced");
        assertThatThrownBy(() -> new JpaLabels(labelRepository, mapper).delete(label))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("referenced");
    }

    @Test
    void shouldTranslateConcurrentTaskKeyAndVersionConflicts() {
        var repository = mock(TaskRepository.class);
        var projects = mock(ProjectRepository.class);
        var labels = mock(LabelRepository.class);
        var tasks = new JpaTasks(repository, projects, labels, mapper);
        var entityProject = new ProjectEntity("Platform", "PLATFORM", null, ProjectStatus.ACTIVE);
        ReflectionTestUtils.setField(entityProject, "id", 1L);
        var task =
                new Task(
                        "PLATFORM-1",
                        "Task",
                        null,
                        TaskStatus.TODO,
                        TaskPriority.HIGH,
                        2L,
                        null,
                        mapper.toDomain(entityProject),
                        Set.of());
        when(projects.getReferenceById(1L)).thenReturn(entityProject);
        when(labels.findAllById(any())).thenReturn(List.of());
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_tasks_task_key"));
        assertThatThrownBy(() -> tasks.save(task))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PLATFORM-1");
        var entity =
                new TaskEntity(
                        "PLATFORM-1",
                        "Task",
                        null,
                        TaskStatus.TODO,
                        TaskPriority.HIGH,
                        2L,
                        null,
                        entityProject,
                        Set.of());
        ReflectionTestUtils.setField(task, "id", 10L);
        ReflectionTestUtils.setField(task, "version", 0L);
        ReflectionTestUtils.setField(entity, "version", 0L);
        when(repository.findById(10L)).thenReturn(Optional.of(entity));
        doThrow(new OptimisticLockingFailureException("stale"))
                .when(repository)
                .saveAndFlush(any());
        assertThatThrownBy(() -> tasks.save(task))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified");
        ReflectionTestUtils.setField(entity, "version", 1L);
        assertThatThrownBy(() -> tasks.save(task))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified");
    }

    @Test
    void shouldPreserveUnexpectedIntegrityFailures() {
        var repository = mock(ProjectRepository.class);
        var error = new DataIntegrityViolationException("different_constraint");
        when(repository.saveAndFlush(any())).thenThrow(error);
        assertThatThrownBy(
                        () ->
                                new JpaProjects(repository, mapper)
                                        .save(
                                                new Project(
                                                        "Platform",
                                                        "PLATFORM",
                                                        null,
                                                        ProjectStatus.ACTIVE)))
                .isSameAs(error);
    }
}
