package itmo.tracker.application.port.out;

import itmo.tracker.application.model.*;
import itmo.tracker.domain.*;

import java.util.*;

public interface ProjectMemberStore {
    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    Optional<ProjectMember> findByIdAndProjectId(Long id, Long projectId);

    boolean existsByProjectIdAndUserIdAndActiveTrue(Long projectId, Long userId);

    ResultPage<ProjectMember> findAllByProjectId(Long projectId, PageQuery page);

    ProjectMember save(ProjectMember member);
}
