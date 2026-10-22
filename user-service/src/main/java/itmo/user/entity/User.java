package itmo.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("users")
@Getter
public class User {
    @Id
    private Long id;
    @NotBlank @Size(max = 100)
    private String name;
    @NotBlank @Email @Size(max = 320)
    private String email;
    @NotNull
    private UserRole role;
    @JsonIgnore
    @Pattern(regexp = "^\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$")
    @Column("password_hash")
    private String passwordHash;
    @Column("created_at")
    private Instant createdAt;
    @Column("updated_at")
    private Instant updatedAt;
    private boolean deleted;
    @Version
    private Long version;

    protected User() {
    }

    public User(String name, String email, UserRole role) {
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public void update(String name, String email, UserRole role) {
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public void delete() {
        deleted = true;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void markSaved(Instant now) {
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }
}
