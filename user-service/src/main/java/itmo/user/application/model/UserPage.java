package itmo.user.application.model;

public record UserPage(int page, int size) {
    public UserPage {
        if (page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException("Page must be nonnegative and size between 1 and 50");
        }
    }
    public long offset() { return (long) page * size; }
}
