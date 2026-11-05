package itmo.tracker.adapter.out.persistence;

import itmo.tracker.application.model.*;
import itmo.tracker.application.port.out.ProjectStore;
import itmo.tracker.domain.Project;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaProjects implements ProjectStore {
    private final ProjectRepository repository;
    private final PersistenceMapper mapper;

    public Optional<Project> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    public boolean existsById(Long id) {
        return repository.existsById(id);
    }

    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    public boolean existsByCodeAndIdNot(String code, Long id) {
        return repository.existsByCodeAndIdNot(code, id);
    }

    public ResultPage<Project> findAll(PageQuery page) {
        return Pages.result(repository.findAll(Pages.request(page)), mapper::toDomain);
    }

    public Project save(Project project) {
        return StorageErrors.run(
                () -> {
                    ProjectEntity entity =
                            project.getId() == null
                                    ? new ProjectEntity(
                                            project.getName(),
                                            project.getCode(),
                                            project.getDescription(),
                                            project.getStatus())
                                    : repository.findById(project.getId()).orElseThrow();
                    entity.update(
                            project.getName(),
                            project.getCode(),
                            project.getDescription(),
                            project.getStatus());
                    return mapper.toDomain(repository.saveAndFlush(entity));
                },
                "uq_projects_code",
                "Project with code '%s' already exists".formatted(project.getCode()),
                null);
    }

    public void delete(Project project) {
        StorageErrors.run(
                () -> {
                    repository.deleteById(project.getId());
                    repository.flush();
                    return null;
                },
                "fk_tasks_project",
                "Project is referenced by tasks and cannot be deleted",
                null);
    }
}
