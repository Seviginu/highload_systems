package itmo.tracker.application.model;

import java.util.List;

public record ResultSlice<T>(List<T> content, boolean hasNext) {
    public ResultSlice {
        content = List.copyOf(content);
    }

    public List<T> getContent() {
        return content;
    }
}
