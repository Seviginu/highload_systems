package itmo.tracker.application.port.out;

import itmo.tracker.application.model.*;
import itmo.tracker.domain.*;

import java.util.*;

public interface TaskStore {
    Optional<Task> findById(Long id);

    boolean existsByTaskKey(String key);

    boolean existsByTaskKeyAndIdNot(String key, Long id);

    Task save(Task task);

    Task saveStatus(Task task);

    void delete(Task task);

    ResultPage<Task> findAllFiltered(
            Long projectId,
            Long authorId,
            Long assigneeId,
            Long labelId,
            TaskStatus status,
            TaskPriority priority,
            PageQuery page);

    ResultSlice<Task> findAllByOrderByIdAsc(PageQuery page);

    ResultSlice<Task> findByIdGreaterThanOrderByIdAsc(Long afterId, PageQuery page);
}
