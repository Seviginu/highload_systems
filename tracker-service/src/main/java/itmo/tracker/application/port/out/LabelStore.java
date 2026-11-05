package itmo.tracker.application.port.out;

import itmo.tracker.application.model.*;
import itmo.tracker.domain.*;

import java.util.*;

public interface LabelStore {
    Optional<Label> findById(Long id);

    List<Label> findAllById(Set<Long> ids);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    ResultPage<Label> findAll(PageQuery page);

    Label save(Label label);

    void delete(Label label);
}
