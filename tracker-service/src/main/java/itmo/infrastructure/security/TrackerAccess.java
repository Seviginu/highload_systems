package itmo.infrastructure.security;

import itmo.project.repository.ProjectMemberRepository;
import itmo.task.entity.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class TrackerAccess {
    private final ProjectMemberRepository members;

    public void requireAdmin(CurrentUser actor) {
        if (actor == null || !actor.isAdmin()) {
            throw new AccessDeniedException("Supervisor role is required");
        }
    }

    public void requireManager(CurrentUser actor) {
        if (actor == null || !(actor.isAdmin() || actor.isTeamLead())) {
            throw new AccessDeniedException("Supervisor or team lead role is required");
        }
    }

    public void requireProjectManager(Long projectId, CurrentUser actor) {
        requireManager(actor);
        if (!actor.isAdmin() && !isMember(projectId, actor.id())) {
            throw new AccessDeniedException("Active project membership is required");
        }
    }

    public void requireStatusChange(Task task, CurrentUser actor) {
        if (actor != null && actor.isAdmin()) {
            return;
        }
        if (actor != null && actor.isTeamLead()) {
            requireProjectManager(task.getProject().getId(), actor);
            return;
        }
        if (actor == null || !actor.roles().contains("DEVELOPER")
                || !Objects.equals(task.getAssigneeId(), actor.id())
                || !isMember(task.getProject().getId(), actor.id())) {
            throw new AccessDeniedException("Only the assigned active project member can change the task status");
        }
    }

    private boolean isMember(Long projectId, long userId) {
        return members.existsByProjectIdAndUserIdAndActiveTrue(projectId, userId);
    }
}
