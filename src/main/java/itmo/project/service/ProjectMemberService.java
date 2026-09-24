package itmo.project.service;

import itmo.project.dto.AddProjectMemberRequest;
import itmo.project.dto.ProjectMemberResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProjectMemberService {

    ProjectMemberResponse add(Long projectId, AddProjectMemberRequest request);

    ProjectMemberResponse assignTeamLead(Long projectId, Long userId);

    Page<ProjectMemberResponse> findAll(Long projectId, Pageable pageable);

    boolean isActiveMember(Long projectId, Long userId);

    void deactivate(Long projectId, Long memberId);
}
