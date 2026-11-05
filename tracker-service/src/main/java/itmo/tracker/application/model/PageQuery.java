package itmo.tracker.application.model;

public record PageQuery(int page, int size) {
    public PageQuery {
        if (page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException("Invalid page or size");
        }
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size);
    }
}
