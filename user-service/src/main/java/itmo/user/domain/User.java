package itmo.user.domain;

import java.time.Instant;

public class User {
    private Long id;
    private String name;
    private String email;
    private UserRole role;
    private String passwordHash;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean deleted;
    private Long version;

    public User(Long id, String name, String email, UserRole role, String passwordHash, Instant createdAt, Instant updatedAt, boolean deleted, Long version) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deleted = deleted;
        this.version = version;
    }

    public User(String name, String email, UserRole role) {
        this(null, name.trim(), normalizeEmail(email), role, null, null, null, false, null);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public void update(String name, String email, UserRole role) {
        this.name = name.trim();
        this.email = normalizeEmail(email);
        this.role = role;
    }

    public void delete() { deleted = true; }
    public void changePasswordHash(String hash) { passwordHash = hash; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public UserRole getRole() { return role; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isDeleted() { return deleted; }
    public Long getVersion() { return version; }
}
