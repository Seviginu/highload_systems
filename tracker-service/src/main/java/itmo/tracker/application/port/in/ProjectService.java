package itmo.tracker.application.port.in;

import itmo.tracker.application.model.Actor;
import itmo.tracker.application.model.CreateProjectCommand;
import itmo.tracker.application.model.PageQuery;
import itmo.tracker.application.model.ProjectView;
import itmo.tracker.application.model.ResultPage;
import itmo.tracker.application.model.UpdateProjectCommand;
import itmo.tracker.domain.Project;

public interface ProjectService {

    ProjectView create(CreateProjectCommand request, Actor actor);

    ProjectView findById(Long id);

    Project requireEntity(Long id);

    ResultPage<ProjectView> findAll(PageQuery pageable);

    ProjectView update(Long id, UpdateProjectCommand request, Actor actor);

    void delete(Long id, Actor actor);
}
