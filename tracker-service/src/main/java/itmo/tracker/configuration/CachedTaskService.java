package itmo.tracker.configuration;

import static itmo.tracker.configuration.TaskCacheConfiguration.TASKS_CACHE;

import itmo.tracker.application.model.*;
import itmo.tracker.application.port.in.TaskService;

import org.springframework.cache.annotation.*;

public class CachedTaskService implements TaskService {
    private final TaskService delegate;

    public CachedTaskService(TaskService delegate) {
        this.delegate = delegate;
    }

    @Override
    @CachePut(cacheNames = TASKS_CACHE, key = "#result.id()")
    public TaskView create(CreateTaskCommand request, Actor actor) {
        return delegate.create(request, actor);
    }

    @Override
    @Cacheable(cacheNames = TASKS_CACHE, key = "#id", sync = true)
    public TaskView findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public ResultPage<TaskView> findAll(TaskFilter filter, PageQuery page) {
        return delegate.findAll(filter, page);
    }

    @Override
    public TaskFeedView findFeed(Long afterId, int limit) {
        return delegate.findFeed(afterId, limit);
    }

    @Override
    @CachePut(cacheNames = TASKS_CACHE, key = "#id")
    public TaskView move(Long id, MoveTaskCommand request, Actor actor) {
        return delegate.move(id, request, actor);
    }

    @Override
    @CachePut(cacheNames = TASKS_CACHE, key = "#id")
    public TaskView update(Long id, UpdateTaskCommand request, Actor actor) {
        return delegate.update(id, request, actor);
    }

    @Override
    @CachePut(cacheNames = TASKS_CACHE, key = "#id")
    public TaskView changeStatus(Long id, ChangeTaskStatusCommand request, Actor actor) {
        return delegate.changeStatus(id, request, actor);
    }

    @Override
    @CacheEvict(cacheNames = TASKS_CACHE, key = "#id")
    public void delete(Long id, Actor actor) {
        delegate.delete(id, actor);
    }
}
