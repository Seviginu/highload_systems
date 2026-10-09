package itmo.label.mapper;

import itmo.label.dto.CreateLabelRequest;
import itmo.label.dto.LabelResponse;
import itmo.label.dto.UpdateLabelRequest;
import itmo.label.entity.Label;
import org.springframework.stereotype.Component;

@Component
public class LabelMapper {

    public Label toEntity(CreateLabelRequest request) {
        return new Label(normalizeName(request.name()), normalizeColor(request.color()));
    }

    public void updateEntity(Label label, UpdateLabelRequest request) {
        label.update(normalizeName(request.name()), normalizeColor(request.color()));
    }

    public LabelResponse toResponse(Label label) {
        return new LabelResponse(label.getId(), label.getName(), label.getColor(), label.getCreatedAt());
    }

    public String normalizeName(String name) {
        return name.trim();
    }

    private String normalizeColor(String color) {
        return color == null ? null : color.toUpperCase();
    }
}
