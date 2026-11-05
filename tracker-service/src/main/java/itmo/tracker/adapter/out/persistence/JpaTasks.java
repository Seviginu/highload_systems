package itmo.tracker.adapter.out.persistence;

import itmo.tracker.application.exception.ConflictException;
import itmo.tracker.application.model.*;
import itmo.tracker.application.port.out.TaskStore;
import itmo.tracker.domain.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class JpaTasks implements TaskStore {
    private final TaskRepository repository;
    private final ProjectRepository projects;
    private final LabelRepository labels;
    private final PersistenceMapper mapper;

    public Optional<Task> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    public boolean existsByTaskKey(String key) {
        return repository.existsByTaskKey(key);
    }

    public boolean existsByTaskKeyAndIdNot(String key, Long id) {
        return repository.existsByTaskKeyAndIdNot(key, id);
    }

    public ResultPage<Task> findAllFiltered(
            Long projectId,
            Long authorId,
            Long assigneeId,
            Long labelId,
            TaskStatus status,
            TaskPriority priority,
            PageQuery page) {
        return Pages.result(
                repository.findAllFiltered(
                        projectId,
                        authorId,
                        assigneeId,
                        labelId,
                        status,
                        priority,
                        Pages.request(page)),
                mapper::toDomain);
    }

    public ResultSlice<Task> findAllByOrderByIdAsc(PageQuery page) {
        var slice = repository.findAllByOrderByIdAsc(Pages.request(page));
        return new ResultSlice<>(
                slice.getContent().stream().map(mapper::toDomain).toList(), slice.hasNext());
    }

    public ResultSlice<Task> findByIdGreaterThanOrderByIdAsc(Long afterId, PageQuery page) {
        var slice = repository.findByIdGreaterThanOrderByIdAsc(afterId, Pages.request(page));
        return new ResultSlice<>(
                slice.getContent().stream().map(mapper::toDomain).toList(), slice.hasNext());
    }

    public Task save(Task task) {
        return save(task, false);
    }

    public Task saveStatus(Task task) {
        return save(task, true);
    }

    private Task save(Task task, boolean statusOnly) {
        String conflict =
                "Task with id '%d' was modified by another request".formatted(task.getId());
        return StorageErrors.run(
                () -> {
                    ProjectEntity project = projects.getReferenceById(task.getProject().getId());
                    Set<LabelEntity> attachedLabels =
                            new LinkedHashSet<>(
                                    labels.findAllById(
                                            task.getLabels().stream().map(Label::getId).toList()));
                    TaskEntity entity;
                    if (task.getId() == null) {
                        entity =
                                new TaskEntity(
                                        task.getTaskKey(),
                                        task.getTitle(),
                                        task.getDescription(),
                                        task.getStatus(),
                                        task.getPriority(),
                                        task.getAuthorId(),
                                        task.getAssigneeId(),
                                        project,
                                        attachedLabels);
                    } else {
                        entity = repository.findById(task.getId()).orElseThrow();
                        if (!Objects.equals(entity.getVersion(), task.getVersion())) {
                            throw new ConflictException(conflict);
                        }
                        if (statusOnly) {
                            entity.changeStatus(task.getStatus());
                        } else {
                            entity.update(
                                    task.getTaskKey(),
                                    task.getTitle(),
                                    task.getDescription(),
                                    task.getStatus(),
                                    task.getPriority(),
                                    task.getAuthorId(),
                                    task.getAssigneeId(),
                                    project,
                                    attachedLabels);
                        }
                    }
                    return mapper.toDomain(repository.saveAndFlush(entity));
                },
                "uq_tasks_task_key",
                "Task with key '%s' already exists".formatted(task.getTaskKey()),
                conflict);
    }

    public void delete(Task task) {
        repository.deleteById(task.getId());
        repository.flush();
    }
}
