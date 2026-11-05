package itmo.tracker.application.model;

import java.util.List;
import java.util.function.Function;

public record ResultPage<T>(List<T> content, long totalElements) {
    public ResultPage {
        content = List.copyOf(content);
    }

    public List<T> getContent() {
        return content;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public <R> ResultPage<R> map(Function<? super T, R> mapper) {
        return new ResultPage<>(content.stream().map(mapper).toList(), totalElements);
    }
}
