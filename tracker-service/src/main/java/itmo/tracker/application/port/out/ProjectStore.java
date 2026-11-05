package itmo.tracker.application.port.out;

import itmo.tracker.application.model.*;
import itmo.tracker.domain.*;

import java.util.*;

public interface ProjectStore {
    Optional<Project> findById(Long id);

    boolean existsById(Long id);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    ResultPage<Project> findAll(PageQuery page);

    Project save(Project project);

    void delete(Project project);
}
