package itmo.tracker.application.mapper;

import itmo.tracker.application.model.CreateLabelCommand;
import itmo.tracker.application.model.LabelView;
import itmo.tracker.application.model.UpdateLabelCommand;
import itmo.tracker.domain.Label;

public class LabelMapper {

    public Label toDomain(CreateLabelCommand request) {
        return new Label(normalizeName(request.name()), normalizeColor(request.color()));
    }

    public void updateDomain(Label label, UpdateLabelCommand request) {
        label.update(normalizeName(request.name()), normalizeColor(request.color()));
    }

    public LabelView toResponse(Label label) {
        return new LabelView(
                label.getId(), label.getName(), label.getColor(), label.getCreatedAt());
    }

    public String normalizeName(String name) {
        return name.trim();
    }

    private String normalizeColor(String color) {
        return color == null ? null : color.toUpperCase();
    }
}
