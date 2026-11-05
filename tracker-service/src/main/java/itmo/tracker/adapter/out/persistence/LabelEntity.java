package itmo.tracker.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;

import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity(name = "Label")
@Table(name = "labels")
@Getter
public class LabelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name must not be blank")
    @Size(max = 50, message = "Name must not exceed 50 characters")
    @Column(nullable = false, length = 50)
    private String name;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must use hexadecimal format #RRGGBB")
    @Column(length = 7)
    private String color;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @SuppressWarnings("unused")
    private Instant createdAt;

    @ManyToMany(mappedBy = "labels")
    @SuppressWarnings("FieldMayBeFinal")
    private Set<TaskEntity> tasks = new LinkedHashSet<>();

    protected LabelEntity() {}

    public LabelEntity(String name, String color) {
        this.name = name;
        this.color = color;
    }

    public void update(String name, String color) {
        this.name = name;
        this.color = color;
    }

    public Set<TaskEntity> getTasks() {
        return Set.copyOf(tasks);
    }

    public void addTask(TaskEntity task) {
        tasks.add(task);
    }

    public void removeTask(TaskEntity task) {
        tasks.remove(task);
    }
}
