package itmo.tracker.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

public class Label {

    private Long id;

    private String name;

    private String color;

    private Instant createdAt;

    private Set<Task> tasks = new LinkedHashSet<>();

    protected Label() {}

    public Label(String name, String color) {
        this.name = name;
        this.color = color;
    }

    public void update(String name, String color) {
        this.name = name;
        this.color = color;
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

    public Label(Long id, String name, String color, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
