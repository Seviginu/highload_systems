package itmo.label.service;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.label.dto.CreateLabelRequest;
import itmo.label.dto.UpdateLabelRequest;
import itmo.label.entity.Label;
import itmo.label.mapper.LabelMapper;
import itmo.label.repository.LabelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabelServiceImplTest {

    @Mock
    private LabelRepository labelRepository;

    private LabelServiceImpl labelService;

    @BeforeEach
    void setUp() {
        labelService = new LabelServiceImpl(labelRepository, new LabelMapper());
    }

    @Test
    void shouldCreateNormalizedLabel() {
        when(labelRepository.existsByNameIgnoreCase("Backend")).thenReturn(false);
        when(labelRepository.saveAndFlush(any(Label.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = labelService.create(new CreateLabelRequest(" Backend ", "#a1b2c3"));

        assertThat(response.name()).isEqualTo("Backend");
        assertThat(response.color()).isEqualTo("#A1B2C3");
    }

    @Test
    void shouldRejectDuplicateNameIgnoringCase() {
        when(labelRepository.existsByNameIgnoreCase("backend")).thenReturn(true);

        assertThatThrownBy(() -> labelService.create(new CreateLabelRequest("backend", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("backend");
        verify(labelRepository, never()).saveAndFlush(any(Label.class));
    }

    @Test
    void shouldTranslateDatabaseConflictDuringCreate() {
        when(labelRepository.existsByNameIgnoreCase("Backend")).thenReturn(false);
        when(labelRepository.saveAndFlush(any(Label.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> labelService.create(new CreateLabelRequest("Backend", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldFindLabelById() {
        when(labelRepository.findById(1L)).thenReturn(Optional.of(label("Backend", "#A1B2C3")));

        assertThat(labelService.findById(1L).name()).isEqualTo("Backend");
    }

    @Test
    void shouldReportMissingLabel() {
        when(labelRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labelService.findById(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void shouldReturnPageOfLabels() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(labelRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(label("Backend", null)), pageable, 1));

        var result = labelService.findAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(response -> response.name()).containsExactly("Backend");
    }

    @Test
    void shouldUpdateLabel() {
        Label label = label("Backend", "#000000");
        when(labelRepository.findById(1L)).thenReturn(Optional.of(label));
        when(labelRepository.existsByNameIgnoreCaseAndIdNot("API", 1L)).thenReturn(false);
        when(labelRepository.saveAndFlush(label)).thenReturn(label);

        var response = labelService.update(1L, new UpdateLabelRequest(" API ", "#abcdef"));

        assertThat(response.name()).isEqualTo("API");
        assertThat(response.color()).isEqualTo("#ABCDEF");
    }

    @Test
    void shouldRejectDuplicateNameDuringUpdate() {
        Label label = label("Backend", null);
        when(labelRepository.findById(1L)).thenReturn(Optional.of(label));
        when(labelRepository.existsByNameIgnoreCaseAndIdNot("Frontend", 1L)).thenReturn(true);

        assertThatThrownBy(() -> labelService.update(1L, new UpdateLabelRequest("Frontend", null)))
                .isInstanceOf(ConflictException.class);
        verify(labelRepository, never()).saveAndFlush(label);
    }

    @Test
    void shouldTranslateDatabaseConflictDuringUpdate() {
        Label label = label("Backend", null);
        when(labelRepository.findById(1L)).thenReturn(Optional.of(label));
        when(labelRepository.existsByNameIgnoreCaseAndIdNot("API", 1L)).thenReturn(false);
        when(labelRepository.saveAndFlush(label))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> labelService.update(1L, new UpdateLabelRequest("API", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldDeleteLabel() {
        Label label = label("Backend", null);
        when(labelRepository.findById(1L)).thenReturn(Optional.of(label));

        labelService.delete(1L);

        verify(labelRepository).delete(label);
        verify(labelRepository).flush();
    }

    @Test
    void shouldRejectDeletionOfReferencedLabel() {
        Label label = label("Backend", null);
        when(labelRepository.findById(1L)).thenReturn(Optional.of(label));
        doThrow(new DataIntegrityViolationException("foreign key")).when(labelRepository).flush();

        assertThatThrownBy(() -> labelService.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("referenced");
    }

    private Label label(String name, String color) {
        return new Label(name, color);
    }
}
