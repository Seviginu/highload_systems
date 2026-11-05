package itmo.tracker.adapter.out.persistence;

import itmo.tracker.application.model.*;
import itmo.tracker.application.port.out.ProjectMemberStore;
import itmo.tracker.domain.ProjectMember;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaMembers implements ProjectMemberStore {
    private final ProjectMemberRepository repository;
    private final ProjectRepository projects;
    private final PersistenceMapper mapper;

    public Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId) {
        return repository.findByProjectIdAndUserId(projectId, userId).map(mapper::toDomain);
    }

    public Optional<ProjectMember> findByIdAndProjectId(Long id, Long projectId) {
        return repository.findByIdAndProjectId(id, projectId).map(mapper::toDomain);
    }

    public boolean existsByProjectIdAndUserIdAndActiveTrue(Long projectId, Long userId) {
        return repository.existsByProjectIdAndUserIdAndActiveTrue(projectId, userId);
    }

    public ResultPage<ProjectMember> findAllByProjectId(Long projectId, PageQuery page) {
        return Pages.result(
                repository.findAllByProjectId(projectId, Pages.request(page)), mapper::toDomain);
    }

    public ProjectMember save(ProjectMember member) {
        Long projectId = member.getProject().getId();
        return StorageErrors.run(
                () -> {
                    ProjectMemberEntity entity =
                            member.getId() == null
                                    ? new ProjectMemberEntity(
                                            projects.getReferenceById(projectId),
                                            member.getUserId())
                                    : repository
                                            .findByIdAndProjectId(member.getId(), projectId)
                                            .orElseThrow();
                    if (member.isActive()) {
                        entity.activate();
                    } else {
                        entity.deactivate();
                    }
                    return mapper.toDomain(repository.saveAndFlush(entity));
                },
                "uq_project_members_project_user",
                "User with id '%d' is already an active member of project '%d'"
                        .formatted(member.getUserId(), projectId),
                null);
    }
}
