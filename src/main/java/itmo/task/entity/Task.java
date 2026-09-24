package itmo.task.entity;

import itmo.label.entity.Label;
import itmo.project.entity.Project;
import itmo.user.entity.User;
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
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Task key must not be blank")
    @Size(max = 32, message = "Task key must not exceed 32 characters")
    @Pattern(
            regexp = "^[A-Z][A-Z0-9_]{1,19}-[1-9][0-9]*$",
            message = "Task key must have format PROJECT-1"
    )
    @Column(name = "task_key", nullable = false, unique = true, length = 32)
    private String taskKey;

    @NotBlank(message = "Title must not be blank")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Column(nullable = false, length = 255)
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
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @NotNull(message = "Project is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToMany
    @BatchSize(size = 50)
    @JoinTable(
            name = "task_labels",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "label_id")
    )
    private Set<Label> labels = new LinkedHashSet<>();

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Task() {
    }

    public Task(
            String taskKey,
            String title,
            String description,
            TaskStatus status,
            TaskPriority priority,
            User author,
            User assignee,
            Project project,
            Set<Label> labels
    ) {
        this.taskKey = taskKey;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.author = author;
        this.assignee = assignee;
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
            User author,
            User assignee,
            Project project,
            Set<Label> labels
    ) {
        this.taskKey = taskKey;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.author = author;
        this.assignee = assignee;
        if (this.project != project) {
            this.project.removeTask(this);
            this.project = project;
            project.addTask(this);
        }
        replaceLabels(labels);
    }

    public void move(Project project, User assignee, Set<Label> labels) {
        if (this.project != project) {
            this.project.removeTask(this);
            this.project = project;
            project.addTask(this);
        }
        this.assignee = assignee;
        replaceLabels(labels);
    }

    private void replaceLabels(Set<Label> newLabels) {
        labels.forEach(label -> label.removeTask(this));
        labels.clear();
        newLabels.forEach(label -> {
            labels.add(label);
            label.addTask(this);
        });
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

    public User getAuthor() {
        return author;
    }

    public User getAssignee() {
        return assignee;
    }

    public Project getProject() {
        return project;
    }

    public Set<Label> getLabels() {
        return Set.copyOf(labels);
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
