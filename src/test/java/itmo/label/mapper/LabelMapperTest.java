package itmo.label.mapper;

import itmo.label.dto.CreateLabelRequest;
import itmo.label.dto.UpdateLabelRequest;
import itmo.label.entity.Label;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabelMapperTest {

    private final LabelMapper mapper = new LabelMapper();

    @Test
    void shouldNormalizeLabelWhenMappingCreateRequest() {
        Label label = mapper.toEntity(new CreateLabelRequest("  Backend  ", "#a1b2c3"));

        assertThat(label.getName()).isEqualTo("Backend");
        assertThat(label.getColor()).isEqualTo("#A1B2C3");
    }

    @Test
    void shouldUpdateLabelAndKeepNullColor() {
        Label label = new Label("Old", "#000000");

        mapper.updateEntity(label, new UpdateLabelRequest(" New ", null));

        assertThat(label.getName()).isEqualTo("New");
        assertThat(label.getColor()).isNull();
        assertThat(mapper.toResponse(label).name()).isEqualTo("New");
    }
}
