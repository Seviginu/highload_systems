package itmo.tracker.application.port.in;

import itmo.tracker.application.model.Actor;
import itmo.tracker.application.model.AddProjectMemberCommand;
import itmo.tracker.application.model.PageQuery;
import itmo.tracker.application.model.ProjectMemberView;
import itmo.tracker.application.model.ResultPage;

public interface ProjectMemberService {

    ProjectMemberView add(Long projectId, AddProjectMemberCommand request, Actor actor);

    @SuppressWarnings("UnusedReturnValue")
    ProjectMemberView assignValidatedTeamLead(Long projectId, Long userId, Actor actor);

    ResultPage<ProjectMemberView> findAll(Long projectId, PageQuery pageable);

    boolean isActiveMember(Long projectId, Long userId);

    void deactivate(Long projectId, Long memberId, Actor actor);
}
