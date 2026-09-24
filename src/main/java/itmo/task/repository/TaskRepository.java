package itmo.task.repository;

import itmo.task.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    boolean existsByTaskKey(String taskKey);

    boolean existsByTaskKeyAndIdNot(String taskKey, Long id);

    @Override
    @EntityGraph(attributePaths = {"author", "assignee", "project", "labels"})
    Optional<Task> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"author", "assignee", "project"})
    Page<Task> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"author", "assignee", "project"})
    Slice<Task> findAllByOrderByIdAsc(Pageable pageable);

    @EntityGraph(attributePaths = {"author", "assignee", "project"})
    Slice<Task> findByIdGreaterThanOrderByIdAsc(Long afterId, Pageable pageable);
}
