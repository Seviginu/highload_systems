package itmo.tracker.application.mapper;

import itmo.tracker.application.model.ProjectMemberView;
import itmo.tracker.domain.ProjectMember;

public class ProjectMemberMapper {

    public ProjectMemberView toResponse(ProjectMember member) {
        return new ProjectMemberView(
                member.getId(),
                member.getProject().getId(),
                member.getUserId(),
                member.getJoinedAt(),
                member.isActive());
    }
}
