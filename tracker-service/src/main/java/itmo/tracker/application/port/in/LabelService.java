package itmo.tracker.application.port.in;

import itmo.tracker.application.model.Actor;
import itmo.tracker.application.model.CreateLabelCommand;
import itmo.tracker.application.model.LabelView;
import itmo.tracker.application.model.PageQuery;
import itmo.tracker.application.model.ResultPage;
import itmo.tracker.application.model.UpdateLabelCommand;
import itmo.tracker.domain.Label;

import java.util.Set;

public interface LabelService {

    LabelView create(CreateLabelCommand request, Actor actor);

    LabelView findById(Long id);

    Set<Label> requireEntities(Set<Long> ids);

    ResultPage<LabelView> findAll(PageQuery pageable);

    LabelView update(Long id, UpdateLabelCommand request, Actor actor);

    void delete(Long id, Actor actor);
}
