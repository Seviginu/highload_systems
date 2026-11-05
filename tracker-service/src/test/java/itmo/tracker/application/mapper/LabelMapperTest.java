package itmo.tracker.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import itmo.tracker.application.model.CreateLabelCommand;
import itmo.tracker.application.model.UpdateLabelCommand;
import itmo.tracker.domain.Label;

import org.junit.jupiter.api.Test;

class LabelMapperTest {

    private final LabelMapper mapper = new LabelMapper();

    @Test
    void shouldNormalizeLabelWhenMappingCreateRequest() {
        Label label = mapper.toDomain(new CreateLabelCommand("  Backend  ", "#a1b2c3"));

        assertThat(label.getName()).isEqualTo("Backend");
        assertThat(label.getColor()).isEqualTo("#A1B2C3");
    }

    @Test
    void shouldUpdateLabelAndKeepNullColor() {
        Label label = new Label("Old", "#000000");

        mapper.updateDomain(label, new UpdateLabelCommand(" New ", null));

        assertThat(label.getName()).isEqualTo("New");
        assertThat(label.getColor()).isNull();
        assertThat(mapper.toResponse(label).name()).isEqualTo("New");
    }
}
