package itmo.tracker.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

public class Task {

    private Long id;

    private String taskKey;

    private String title;

    private String description;

    private TaskStatus status;

    private TaskPriority priority;

    private Long authorId;

    private Long assigneeId;

    private Project project;

    private Set<Label> labels = new LinkedHashSet<>();

    private Long version;

    private Instant createdAt;

    private Instant updatedAt;

    protected Task() {}

    public Task(
            String taskKey,
            String title,
            String description,
            TaskStatus status,
            TaskPriority priority,
            Long authorId,
            Long assigneeId,
            Project project,
            Set<Label> labels) {
        this.taskKey = taskKey;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.authorId = authorId;
        this.assigneeId = assigneeId;
        this.project = project;
        project.addTask(this);
        replaceLabels(labels);
    }

    public void update(
            String taskKey,
            String title,
            String description,
            TaskStatus status,
            TaskPriority priority,
            Long authorId,
            Long assigneeId,
            Project project,
            Set<Label> labels) {
        this.taskKey = taskKey;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.authorId = authorId;
        this.assigneeId = assigneeId;
        if (this.project != project) {
            this.project.removeTask(this);
            this.project = project;
            project.addTask(this);
        }
        replaceLabels(labels);
    }

    public void changeStatus(TaskStatus status) {
        this.status = status;
    }

    public void move(Project project, Long assigneeId, Set<Label> labels) {
        if (this.project != project) {
            this.project.removeTask(this);
            this.project = project;
            project.addTask(this);
        }
        this.assigneeId = assigneeId;
        replaceLabels(labels);
    }

    private void replaceLabels(Set<Label> newLabels) {
        labels.forEach(label -> label.removeTask(this));
        labels.clear();
        newLabels.forEach(
                label -> {
                    labels.add(label);
                    label.addTask(this);
                });
    }

    public Set<Label> getLabels() {
        return Set.copyOf(labels);
    }

    public Task(
            Long id,
            String taskKey,
            String title,
            String description,
            TaskStatus status,
            TaskPriority priority,
            Long authorId,
            Long assigneeId,
            Project project,
            Long version,
            Instant createdAt,
            Instant updatedAt,
            Set<Label> labels) {
        this.id = id;
        this.taskKey = taskKey;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.authorId = authorId;
        this.assigneeId = assigneeId;
        this.project = project;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.labels = new LinkedHashSet<>(labels);
    }

    public Long getId() {
        return id;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public Project getProject() {
        return project;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
