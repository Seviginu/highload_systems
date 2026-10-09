package itmo.label.controller;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.common.web.GlobalExceptionHandler;
import itmo.label.dto.CreateLabelRequest;
import itmo.label.dto.LabelResponse;
import itmo.label.dto.UpdateLabelRequest;
import itmo.label.service.LabelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LabelControllerTest {

    @Mock
    private LabelService labelService;

    private MockMvc mockMvc;
    private LabelResponse response;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LabelController(labelService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        response = new LabelResponse(
                1L,
                "Backend",
                "#A1B2C3",
                Instant.parse("2026-09-25T00:00:00Z")
        );
    }

    @Test
    void shouldCreateLabel() throws Exception {
        when(labelService.create(any(CreateLabelRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/labels/1"))
                .andExpect(jsonPath("$.name").value("Backend"))
                .andExpect(jsonPath("$.color").value("#A1B2C3"));
    }

    @Test
    void shouldGetLabelById() throws Exception {
        when(labelService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/labels/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturnPageAndTotalCount() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id"));
        when(labelService.findAll(pageable)).thenReturn(new PageImpl<>(List.of(response), pageable, 1));

        mockMvc.perform(get("/api/v1/labels"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].name").value("Backend"));
    }

    @Test
    void shouldUpdateLabel() throws Exception {
        when(labelService.update(any(Long.class), any(UpdateLabelRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/labels/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Backend"));
    }

    @Test
    void shouldDeleteLabel() throws Exception {
        mockMvc.perform(delete("/api/v1/labels/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(labelService).delete(1L);
    }

    @Test
    void shouldReturnValidationErrors() throws Exception {
        mockMvc.perform(post("/api/v1/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": " ", "color": "red"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void shouldReturnNotFoundError() throws Exception {
        when(labelService.findById(42L)).thenThrow(new ResourceNotFoundException("Label", 42L));

        mockMvc.perform(get("/api/v1/labels/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Label with id '42' was not found"));
    }

    @Test
    void shouldReturnConflictError() throws Exception {
        when(labelService.create(any(CreateLabelRequest.class)))
                .thenThrow(new ConflictException("Label already exists"));

        mockMvc.perform(post("/api/v1/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Label already exists"));
    }

    private String validRequest() {
        return """
                {"name": "Backend", "color": "#A1B2C3"}
                """;
    }
}
