package itmo.tracker.application.service;

import itmo.tracker.application.exception.ForbiddenException;
import itmo.tracker.application.model.Actor;
import itmo.tracker.application.port.in.*;
import itmo.tracker.application.port.out.ProjectMemberStore;
import itmo.tracker.domain.Task;

import java.util.Objects;

public class TrackerAccess {
    public TrackerAccess(ProjectMemberStore members) {
        this.members = members;
    }

    private final ProjectMemberStore members;

    public void requireAdmin(Actor actor) {
        if (actor == null || !actor.isAdmin()) {
            throw new ForbiddenException("Supervisor role is required");
        }
    }

    public void requireManager(Actor actor) {
        if (actor == null || !(actor.isAdmin() || actor.isTeamLead())) {
            throw new ForbiddenException("Supervisor or team lead role is required");
        }
    }

    public void requireProjectManager(Long projectId, Actor actor) {
        requireManager(actor);
        if (!actor.isAdmin() && !isMember(projectId, actor.id())) {
            throw new ForbiddenException("Active project membership is required");
        }
    }

    public void requireStatusChange(Task task, Actor actor) {
        if (actor != null && actor.isAdmin()) {
            return;
        }
        if (actor != null && actor.isTeamLead()) {
            requireProjectManager(task.getProject().getId(), actor);
            return;
        }
        if (actor == null
                || !actor.roles().contains("DEVELOPER")
                || !Objects.equals(task.getAssigneeId(), actor.id())
                || !isMember(task.getProject().getId(), actor.id())) {
            throw new ForbiddenException(
                    "Only the assigned active project member can change the task status");
        }
    }

    private boolean isMember(Long projectId, long userId) {
        return members.existsByProjectIdAndUserIdAndActiveTrue(projectId, userId);
    }
}
