package itmo.label.service;

import itmo.infrastructure.security.CurrentUser;
import itmo.label.dto.CreateLabelRequest;
import itmo.label.dto.LabelResponse;
import itmo.label.dto.UpdateLabelRequest;
import itmo.label.entity.Label;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Set;

public interface LabelService {

    LabelResponse create(CreateLabelRequest request, CurrentUser actor);

    LabelResponse findById(Long id);

    Set<Label> requireEntities(Set<Long> ids);

    Page<LabelResponse> findAll(Pageable pageable);

    LabelResponse update(Long id, UpdateLabelRequest request, CurrentUser actor);

    void delete(Long id, CurrentUser actor);
}
