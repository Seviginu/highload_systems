package itmo.files.application;

public record Actor(long id, boolean admin) {
    public Actor {
        if (id <= 0) throw new IllegalArgumentException("Invalid actor");
    }
}
