package itmo.task.repository;

import itmo.task.entity.Task;
import itmo.task.entity.TaskPriority;
import itmo.task.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

@SuppressWarnings("NullableProblems")
public interface TaskRepository extends JpaRepository<Task, Long> {

    boolean existsByTaskKey(String taskKey);

    boolean existsByTaskKeyAndIdNot(String taskKey, Long id);

    @Override
    @EntityGraph(attributePaths = {"project", "labels"})
    Optional<Task> findById(Long id);

    @EntityGraph(attributePaths = {"project"})
    @Query(
            value = """
                    select task
                    from Task task
                    where (:projectId is null or task.project.id = :projectId)
                      and (:authorId is null or task.authorId = :authorId)
                      and (:assigneeId is null or task.assigneeId = :assigneeId)
                      and (:status is null or task.status = :status)
                      and (:priority is null or task.priority = :priority)
                      and (:labelId is null or exists (
                          select label.id
                          from task.labels label
                          where label.id = :labelId
                      ))
                    """,
            countQuery = """
                    select count(task)
                    from Task task
                    where (:projectId is null or task.project.id = :projectId)
                      and (:authorId is null or task.authorId = :authorId)
                      and (:assigneeId is null or task.assigneeId = :assigneeId)
                      and (:status is null or task.status = :status)
                      and (:priority is null or task.priority = :priority)
                      and (:labelId is null or exists (
                          select label.id
                          from task.labels label
                          where label.id = :labelId
                      ))
                    """
    )
    Page<Task> findAllFiltered(
            @Param("projectId") Long projectId,
            @Param("authorId") Long authorId,
            @Param("assigneeId") Long assigneeId,
            @Param("labelId") Long labelId,
            @Param("status") TaskStatus status,
            @Param("priority") TaskPriority priority,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"project"})
    Slice<Task> findAllByOrderByIdAsc(Pageable pageable);

    @EntityGraph(attributePaths = {"project"})
    Slice<Task> findByIdGreaterThanOrderByIdAsc(Long afterId, Pageable pageable);
}
