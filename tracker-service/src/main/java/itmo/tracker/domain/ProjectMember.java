package itmo.tracker.domain;

import java.time.Instant;

public class ProjectMember {

    private Long id;

    private Project project;

    private Long userId;

    private Instant joinedAt;

    private boolean active = true;

    protected ProjectMember() {}

    public ProjectMember(Project project, Long userId) {
        this.project = project;
        this.userId = userId;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public ProjectMember(Long id, Project project, Long userId, Instant joinedAt, boolean active) {
        this.id = id;
        this.project = project;
        this.userId = userId;
        this.joinedAt = joinedAt;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public boolean isActive() {
        return active;
    }
}
