package itmo.tracker.adapter.out.persistence;

import itmo.tracker.domain.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity(name = "Task")
@Table(name = "tasks")
@Getter
public class TaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "TaskEntity key must not be blank")
    @Size(max = 32, message = "TaskEntity key must not exceed 32 characters")
    @Pattern(
            regexp = "^[A-Z][A-Z0-9_]{1,19}-[1-9]\\d*$",
            message = "TaskEntity key must have format PROJECT-1")
    @Column(name = "task_key", nullable = false, unique = true, length = 32)
    private String taskKey;

    @NotBlank(message = "Title must not be blank")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status;

    @NotNull(message = "Priority is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskPriority priority;

    @NotNull(message = "Author is required")
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @NotNull(message = "ProjectEntity is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @ManyToMany
    @BatchSize(size = 50)
    @JoinTable(
            name = "task_labels",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "label_id"))
    @SuppressWarnings("FieldMayBeFinal")
    private Set<LabelEntity> labels = new LinkedHashSet<>();

    @Version
    @Column(nullable = false)
    @SuppressWarnings("unused")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @SuppressWarnings("unused")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    @SuppressWarnings("unused")
    private Instant updatedAt;

    protected TaskEntity() {}

    @SuppressWarnings("java:S107")
    public TaskEntity(
            String taskKey,
            String title,
            String description,
            TaskStatus status,
            TaskPriority priority,
            Long authorId,
            Long assigneeId,
            ProjectEntity project,
            Set<LabelEntity> labels) {
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

    @SuppressWarnings("java:S107")
    public void update(
            String taskKey,
            String title,
            String description,
            TaskStatus status,
            TaskPriority priority,
            Long authorId,
            Long assigneeId,
            ProjectEntity project,
            Set<LabelEntity> labels) {
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

    public void move(ProjectEntity project, Long assigneeId, Set<LabelEntity> labels) {
        if (this.project != project) {
            this.project.removeTask(this);
            this.project = project;
            project.addTask(this);
        }
        this.assigneeId = assigneeId;
        replaceLabels(labels);
    }

    private void replaceLabels(Set<LabelEntity> newLabels) {
        labels.forEach(label -> label.removeTask(this));
        labels.clear();
        newLabels.forEach(
                label -> {
                    labels.add(label);
                    label.addTask(this);
                });
    }

    public Set<LabelEntity> getLabels() {
        return Set.copyOf(labels);
    }
}
