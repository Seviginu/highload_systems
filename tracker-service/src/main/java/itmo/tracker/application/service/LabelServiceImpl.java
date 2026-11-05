package itmo.tracker.application.service;

import itmo.tracker.application.exception.ConflictException;
import itmo.tracker.application.exception.ResourceNotFoundException;
import itmo.tracker.application.mapper.LabelMapper;
import itmo.tracker.application.model.Actor;
import itmo.tracker.application.model.CreateLabelCommand;
import itmo.tracker.application.model.LabelView;
import itmo.tracker.application.model.PageQuery;
import itmo.tracker.application.model.ResultPage;
import itmo.tracker.application.model.UpdateLabelCommand;
import itmo.tracker.application.port.in.*;
import itmo.tracker.application.port.out.LabelStore;
import itmo.tracker.domain.Label;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class LabelServiceImpl implements LabelService {
    public LabelServiceImpl(
            TrackerAccess access,
            LabelStore labelRepository,
            LabelMapper labelMapper,
            itmo.tracker.application.port.out.UnitOfWork transactions) {
        this.access = access;
        this.labelRepository = labelRepository;
        this.labelMapper = labelMapper;
        this.transactions = transactions;
    }

    private final TrackerAccess access;

    private final LabelStore labelRepository;
    private final LabelMapper labelMapper;
    private final itmo.tracker.application.port.out.UnitOfWork transactions;

    @Override
    public LabelView create(CreateLabelCommand request, Actor actor) {
        return transactions.write(
                () -> {
                    access.requireAdmin(actor);
                    String normalizedName = labelMapper.normalizeName(request.name());
                    ensureNameAvailable(normalizedName);

                    return labelMapper.toResponse(
                            labelRepository.save(labelMapper.toDomain(request)));
                });
    }

    @Override
    public LabelView findById(Long id) {
        return transactions.read(
                () -> {
                    return labelMapper.toResponse(findEntity(id));
                });
    }

    @Override
    public Set<Label> requireEntities(Set<Long> ids) {
        return transactions.read(
                () -> {
                    if (ids == null || ids.isEmpty()) {
                        return new LinkedHashSet<>();
                    }

                    var labels = labelRepository.findAllById(ids);
                    Set<Long> foundIds =
                            labels.stream().map(Label::getId).collect(Collectors.toSet());
                    for (Long id : ids) {
                        if (!foundIds.contains(id)) {
                            throw new ResourceNotFoundException("Label", id);
                        }
                    }
                    return new LinkedHashSet<>(labels);
                });
    }

    @Override
    public ResultPage<LabelView> findAll(PageQuery pageable) {
        return transactions.read(
                () -> {
                    return labelRepository.findAll(pageable).map(labelMapper::toResponse);
                });
    }

    @Override
    public LabelView update(Long id, UpdateLabelCommand request, Actor actor) {
        return transactions.write(
                () -> {
                    access.requireAdmin(actor);
                    Label label = findEntity(id);
                    String normalizedName = labelMapper.normalizeName(request.name());

                    if (labelRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
                        throw nameConflict(normalizedName);
                    }

                    labelMapper.updateDomain(label, request);

                    return labelMapper.toResponse(labelRepository.save(label));
                });
    }

    @Override
    public void delete(Long id, Actor actor) {
        transactions.write(
                () -> {
                    access.requireAdmin(actor);
                    Label label = findEntity(id);

                    labelRepository.delete(label);

                    return null;
                });
    }

    private Label findEntity(Long id) {
        return labelRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Label", id));
    }

    private void ensureNameAvailable(String name) {
        if (labelRepository.existsByNameIgnoreCase(name)) {
            throw nameConflict(name);
        }
    }

    private ConflictException nameConflict(String name) {
        return new ConflictException("Label with name '%s' already exists".formatted(name));
    }
}
