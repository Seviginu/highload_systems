package itmo.label.service;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.label.dto.CreateLabelRequest;
import itmo.label.dto.LabelResponse;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        CreateLabelRequest request = new CreateLabelRequest("backend", null);

        assertThatThrownBy(() -> labelService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("backend");
        verify(labelRepository, never()).saveAndFlush(any(Label.class));
    }

    @Test
    void shouldTranslateDatabaseConflictDuringCreate() {
        when(labelRepository.existsByNameIgnoreCase("Backend")).thenReturn(false);
        when(labelRepository.saveAndFlush(any(Label.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        CreateLabelRequest request = new CreateLabelRequest("Backend", null);

        assertThatThrownBy(() -> labelService.create(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldFindLabelById() {
        when(labelRepository.findById(1L)).thenReturn(Optional.of(label("#A1B2C3")));

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
    void shouldResolveLabelsInSingleBatch() {
        Label first = label("#A1B2C3");
        Label second = new Label("Frontend", "#C3B2A1");
        ReflectionTestUtils.setField(first, "id", 1L);
        ReflectionTestUtils.setField(second, "id", 2L);
        when(labelRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(first, second));

        var result = labelService.requireEntities(Set.of(1L, 2L));

        assertThat(result).containsExactly(first, second);
        verify(labelRepository).findAllById(Set.of(1L, 2L));
    }

    @Test
    void shouldReportMissingLabelFromBatch() {
        Label label = label("#A1B2C3");
        ReflectionTestUtils.setField(label, "id", 1L);
        when(labelRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(label));

        assertThatThrownBy(() -> labelService.requireEntities(Set.of(1L, 2L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("2");
    }

    @Test
    void shouldReturnPageOfLabels() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(labelRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(label(null)), pageable, 1));

        var result = labelService.findAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(LabelResponse::name).containsExactly("Backend");
    }

    @Test
    void shouldUpdateLabel() {
        Label label = label("#000000");
        when(labelRepository.findById(1L)).thenReturn(Optional.of(label));
        when(labelRepository.existsByNameIgnoreCaseAndIdNot("API", 1L)).thenReturn(false);
        when(labelRepository.saveAndFlush(label)).thenReturn(label);

        var response = labelService.update(1L, new UpdateLabelRequest(" API ", "#abcdef"));

        assertThat(response.name()).isEqualTo("API");
        assertThat(response.color()).isEqualTo("#ABCDEF");
    }

    @Test
    void shouldRejectDuplicateNameDuringUpdate() {
        Label label = label(null);
        when(labelRepository.findById(1L)).thenReturn(Optional.of(label));
        when(labelRepository.existsByNameIgnoreCaseAndIdNot("Frontend", 1L)).thenReturn(true);
        UpdateLabelRequest request = new UpdateLabelRequest("Frontend", null);

        assertThatThrownBy(() -> labelService.update(1L, request))
                .isInstanceOf(ConflictException.class);
        verify(labelRepository, never()).saveAndFlush(label);
    }

    @Test
    void shouldTranslateDatabaseConflictDuringUpdate() {
        Label label = label(null);
        when(labelRepository.findById(1L)).thenReturn(Optional.of(label));
        when(labelRepository.existsByNameIgnoreCaseAndIdNot("API", 1L)).thenReturn(false);
        when(labelRepository.saveAndFlush(label))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        UpdateLabelRequest request = new UpdateLabelRequest("API", null);

        assertThatThrownBy(() -> labelService.update(1L, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldDeleteLabel() {
        Label label = label(null);
        when(labelRepository.findById(1L)).thenReturn(Optional.of(label));

        labelService.delete(1L);

        verify(labelRepository).delete(label);
        verify(labelRepository).flush();
    }

    @Test
    void shouldRejectDeletionOfReferencedLabel() {
        Label label = label(null);
        when(labelRepository.findById(1L)).thenReturn(Optional.of(label));
        doThrow(new DataIntegrityViolationException("foreign key")).when(labelRepository).flush();

        assertThatThrownBy(() -> labelService.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("referenced");
    }

    private Label label(String color) {
        return new Label("Backend", color);
    }
}
