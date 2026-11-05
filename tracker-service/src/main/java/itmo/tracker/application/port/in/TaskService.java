package itmo.tracker.application.port.in;

import itmo.tracker.application.model.Actor;
import itmo.tracker.application.model.ChangeTaskStatusCommand;
import itmo.tracker.application.model.CreateTaskCommand;
import itmo.tracker.application.model.MoveTaskCommand;
import itmo.tracker.application.model.PageQuery;
import itmo.tracker.application.model.ResultPage;
import itmo.tracker.application.model.TaskFeedView;
import itmo.tracker.application.model.TaskFilter;
import itmo.tracker.application.model.TaskView;
import itmo.tracker.application.model.UpdateTaskCommand;

public interface TaskService {
    TaskView changeStatus(Long id, ChangeTaskStatusCommand request, Actor actor);

    TaskView create(CreateTaskCommand request, Actor actor);

    TaskView findById(Long id);

    ResultPage<TaskView> findAll(TaskFilter filter, PageQuery pageable);

    TaskFeedView findFeed(Long afterId, int limit);

    TaskView move(Long id, MoveTaskCommand request, Actor actor);

    TaskView update(Long id, UpdateTaskCommand request, Actor actor);

    void delete(Long id, Actor actor);
}
