package itmo.tracker.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import itmo.tracker.application.exception.ConflictException;
import itmo.tracker.application.exception.ResourceNotFoundException;
import itmo.tracker.application.mapper.LabelMapper;
import itmo.tracker.application.model.CreateLabelCommand;
import itmo.tracker.application.model.LabelView;
import itmo.tracker.application.model.PageQuery;
import itmo.tracker.application.model.ResultPage;
import itmo.tracker.application.model.UpdateLabelCommand;
import itmo.tracker.application.port.out.LabelStore;
import itmo.tracker.domain.Label;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class LabelServiceImplTest {

    @Mock private LabelStore labelRepository;

    private LabelServiceImpl labelService;

    @BeforeEach
    void setUp() {
        labelService =
                new LabelServiceImpl(
                        itmo.support.TestActors.access(),
                        labelRepository,
                        new LabelMapper(),
                        new itmo.support.TestTransactions());
    }

    @Test
    void shouldCreateNormalizedLabel() {
        when(labelRepository.existsByNameIgnoreCase("Backend")).thenReturn(false);
        when(labelRepository.save(any(Label.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response =
                labelService.create(
                        new CreateLabelCommand(" Backend ", "#a1b2c3"),
                        itmo.support.TestActors.admin());

        assertThat(response.name()).isEqualTo("Backend");
        assertThat(response.color()).isEqualTo("#A1B2C3");
    }

    @Test
    void shouldRejectDuplicateNameIgnoringCase() {
        when(labelRepository.existsByNameIgnoreCase("backend")).thenReturn(true);
        CreateLabelCommand request = new CreateLabelCommand("backend", null);

        assertThatThrownBy(() -> labelService.create(request, itmo.support.TestActors.admin()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("backend");
        verify(labelRepository, never()).save(any(Label.class));
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
        PageQuery pageable = PageQuery.of(0, 20);
        when(labelRepository.findAll(pageable))
                .thenReturn(new ResultPage<>(List.of(label(null)), 1));

        var result = labelService.findAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(LabelView::name).containsExactly("Backend");
    }

    @Test
    void shouldUpdateLabel() {
        Label label = label("#000000");
        when(labelRepository.findById(1L)).thenReturn(Optional.of(label));
        when(labelRepository.existsByNameIgnoreCaseAndIdNot("API", 1L)).thenReturn(false);
        when(labelRepository.save(label)).thenReturn(label);

        var response =
                labelService.update(
                        1L,
                        new UpdateLabelCommand(" API ", "#abcdef"),
                        itmo.support.TestActors.admin());

        assertThat(response.name()).isEqualTo("API");
        assertThat(response.color()).isEqualTo("#ABCDEF");
    }

    @Test
    void shouldRejectDuplicateNameDuringUpdate() {
        Label label = label(null);
        when(labelRepository.findById(1L)).thenReturn(Optional.of(label));
        when(labelRepository.existsByNameIgnoreCaseAndIdNot("Frontend", 1L)).thenReturn(true);
        UpdateLabelCommand request = new UpdateLabelCommand("Frontend", null);

        assertThatThrownBy(() -> labelService.update(1L, request, itmo.support.TestActors.admin()))
                .isInstanceOf(ConflictException.class);
        verify(labelRepository, never()).save(label);
    }

    @Test
    void shouldDeleteLabel() {
        Label label = label(null);
        when(labelRepository.findById(1L)).thenReturn(Optional.of(label));

        labelService.delete(1L, itmo.support.TestActors.admin());

        verify(labelRepository).delete(label);
    }

    private Label label(String color) {
        return new Label("Backend", color);
    }
}
