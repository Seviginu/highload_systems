package itmo.task.service;

import itmo.task.dto.CreateTaskRequest;
import itmo.task.dto.TaskResponse;
import itmo.task.dto.UpdateTaskRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TaskService {

    TaskResponse create(CreateTaskRequest request);

    TaskResponse findById(Long id);

    Page<TaskResponse> findAll(Pageable pageable);

    TaskResponse update(Long id, UpdateTaskRequest request);

    void delete(Long id);
}
