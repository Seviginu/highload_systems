package itmo.label.service;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.label.dto.CreateLabelRequest;
import itmo.label.dto.LabelResponse;
import itmo.label.dto.UpdateLabelRequest;
import itmo.label.entity.Label;
import itmo.label.mapper.LabelMapper;
import itmo.label.repository.LabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static itmo.common.persistence.ConstraintViolationDetector.isViolationOf;

@Service
@RequiredArgsConstructor
public class LabelServiceImpl implements LabelService {

    private final LabelRepository labelRepository;
    private final LabelMapper labelMapper;

    @Override
    @Transactional
    public LabelResponse create(CreateLabelRequest request) {
        String normalizedName = labelMapper.normalizeName(request.name());
        ensureNameAvailable(normalizedName);

        try {
            return labelMapper.toResponse(labelRepository.saveAndFlush(labelMapper.toEntity(request)));
        } catch (DataIntegrityViolationException exception) {
            if (isViolationOf(exception, "uq_labels_name_lower")) {
                throw nameConflict(normalizedName);
            }
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LabelResponse findById(Long id) {
        return labelMapper.toResponse(findEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Label> requireEntities(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new LinkedHashSet<>();
        }

        var labels = labelRepository.findAllById(ids);
        Set<Long> foundIds = labels.stream()
                .map(Label::getId)
                .collect(Collectors.toSet());
        for (Long id : ids) {
            if (!foundIds.contains(id)) {
                throw new ResourceNotFoundException("Label", id);
            }
        }
        return new LinkedHashSet<>(labels);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LabelResponse> findAll(Pageable pageable) {
        return labelRepository.findAll(pageable).map(labelMapper::toResponse);
    }

    @Override
    @Transactional
    public LabelResponse update(Long id, UpdateLabelRequest request) {
        Label label = findEntity(id);
        String normalizedName = labelMapper.normalizeName(request.name());

        if (labelRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw nameConflict(normalizedName);
        }

        labelMapper.updateEntity(label, request);
        try {
            return labelMapper.toResponse(labelRepository.saveAndFlush(label));
        } catch (DataIntegrityViolationException exception) {
            if (isViolationOf(exception, "uq_labels_name_lower")) {
                throw nameConflict(normalizedName);
            }
            throw exception;
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Label label = findEntity(id);
        try {
            labelRepository.delete(label);
            labelRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            if (isViolationOf(exception, "fk_task_labels_label")) {
                throw new ConflictException("Label is referenced by tasks and cannot be deleted");
            }
            throw exception;
        }
    }

    private Label findEntity(Long id) {
        return labelRepository.findById(id)
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
