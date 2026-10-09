package itmo.project.mapper;

import itmo.project.dto.ProjectMemberResponse;
import itmo.project.entity.ProjectMember;
import org.springframework.stereotype.Component;

@Component
public class ProjectMemberMapper {

    public ProjectMemberResponse toResponse(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getId(),
                member.getProject().getId(),
                member.getUserId(),
                member.getJoinedAt(),
                member.isActive()
        );
    }
}
