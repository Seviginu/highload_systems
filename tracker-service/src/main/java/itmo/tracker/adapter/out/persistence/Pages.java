package itmo.tracker.adapter.out.persistence;

import itmo.tracker.application.model.PageQuery;
import itmo.tracker.application.model.ResultPage;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.function.Function;

final class Pages {
    private Pages() {}

    static PageRequest request(PageQuery page) {
        return PageRequest.of(page.page(), page.size(), Sort.by(Sort.Direction.ASC, "id"));
    }

    static <E, D> ResultPage<D> result(Page<E> page, Function<E, D> mapper) {
        return new ResultPage<>(
                page.getContent().stream().map(mapper).toList(), page.getTotalElements());
    }
}
