package itmo.tracker.adapter.out.persistence;

import itmo.tracker.application.model.*;
import itmo.tracker.application.port.out.LabelStore;
import itmo.tracker.domain.Label;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class JpaLabels implements LabelStore {
    private final LabelRepository repository;
    private final PersistenceMapper mapper;

    public Optional<Label> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    public List<Label> findAllById(Set<Long> ids) {
        return repository.findAllById(ids).stream().map(mapper::toDomain).toList();
    }

    public boolean existsByNameIgnoreCase(String name) {
        return repository.existsByNameIgnoreCase(name);
    }

    public boolean existsByNameIgnoreCaseAndIdNot(String name, Long id) {
        return repository.existsByNameIgnoreCaseAndIdNot(name, id);
    }

    public ResultPage<Label> findAll(PageQuery page) {
        return Pages.result(repository.findAll(Pages.request(page)), mapper::toDomain);
    }

    public Label save(Label label) {
        return StorageErrors.run(
                () -> {
                    LabelEntity entity =
                            label.getId() == null
                                    ? new LabelEntity(label.getName(), label.getColor())
                                    : repository.findById(label.getId()).orElseThrow();
                    entity.update(label.getName(), label.getColor());
                    return mapper.toDomain(repository.saveAndFlush(entity));
                },
                "uq_labels_name_lower",
                "Label with name '%s' already exists".formatted(label.getName()),
                null);
    }

    public void delete(Label label) {
        StorageErrors.run(
                () -> {
                    repository.deleteById(label.getId());
                    repository.flush();
                    return null;
                },
                "fk_task_labels_label",
                "Label is referenced by tasks and cannot be deleted",
                null);
    }
}
