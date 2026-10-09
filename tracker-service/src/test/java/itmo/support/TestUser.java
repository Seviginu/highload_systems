package itmo.support;

/** User reference supplied by the HTTP stub, never persisted in tracker-db. */
public class TestUser {
    private Long id;
    private final String role;

    public TestUser(String role) {
        this.role = role;
    }

    public Long getId() { return id; }
    public String getRole() { return role; }
    public void assignId(Long id) { this.id = id; }
}
