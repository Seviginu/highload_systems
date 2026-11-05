package itmo.tracker.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

public class Project {

    private Long id;

    private String name;

    private String code;

    private String description;

    private ProjectStatus status;

    private Instant createdAt;

    private Instant updatedAt;

    private Set<Task> tasks = new LinkedHashSet<>();

    protected Project() {}

    public Project(String name, String code, String description, ProjectStatus status) {
        this.name = name;
        this.code = code;
        this.description = description;
        this.status = status;
    }

    public void update(String name, String code, String description, ProjectStatus status) {
        this.name = name;
        this.code = code;
        this.description = description;
        this.status = status;
    }

    public Set<Task> getTasks() {
        return Set.copyOf(tasks);
    }

    public void addTask(Task task) {
        tasks.add(task);
    }

    public void removeTask(Task task) {
        tasks.remove(task);
    }

    public Project(
            Long id,
            String name,
            String code,
            String description,
            ProjectStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
