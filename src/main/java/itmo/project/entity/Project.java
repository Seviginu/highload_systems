package itmo.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name must not be blank")
    @Size(max = 150, message = "Name must not exceed 150 characters")
    @Column(nullable = false, length = 150)
    private String name;

    @NotBlank(message = "Code must not be blank")
    @Size(min = 2, max = 20, message = "Code must contain from 2 to 20 characters")
    @Pattern(
            regexp = "^[A-Z][A-Z0-9_]{1,19}$",
            message = "Code must start with a letter and contain only uppercase letters, digits or underscores"
    )
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(columnDefinition = "text")
    private String description;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Project() {
    }

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
