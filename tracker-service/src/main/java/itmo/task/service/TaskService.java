package itmo.task.service;

import itmo.infrastructure.security.CurrentUser;
import itmo.task.dto.CreateTaskRequest;
import itmo.task.dto.ChangeTaskStatusRequest;
import itmo.task.dto.MoveTaskRequest;
import itmo.task.dto.TaskFeedResponse;
import itmo.task.dto.TaskFilter;
import itmo.task.dto.TaskResponse;
import itmo.task.dto.UpdateTaskRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TaskService {
    TaskResponse changeStatus(Long id, ChangeTaskStatusRequest request, CurrentUser actor);

    TaskResponse create(CreateTaskRequest request, CurrentUser actor);

    TaskResponse findById(Long id);

    Page<TaskResponse> findAll(TaskFilter filter, Pageable pageable);

    TaskFeedResponse findFeed(Long afterId, int limit);

    TaskResponse move(Long id, MoveTaskRequest request, CurrentUser actor);

    TaskResponse update(Long id, UpdateTaskRequest request, CurrentUser actor);

    void delete(Long id, CurrentUser actor);
}
