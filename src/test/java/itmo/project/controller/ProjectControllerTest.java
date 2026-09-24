package itmo.project.controller;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.common.web.GlobalExceptionHandler;
import itmo.project.dto.CreateProjectRequest;
import itmo.project.dto.ProjectResponse;
import itmo.project.dto.UpdateProjectRequest;
import itmo.project.entity.ProjectStatus;
import itmo.project.service.ProjectService;
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
import static org.mockito.Mockito.doNothing;
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
class ProjectControllerTest {

    @Mock
    private ProjectService projectService;

    private MockMvc mockMvc;
    private ProjectResponse response;

    @BeforeEach
    void setUp() {
        ProjectController controller = new ProjectController(projectService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        response = new ProjectResponse(
                1L,
                "Platform",
                "PLATFORM",
                "Main platform",
                ProjectStatus.ACTIVE,
                Instant.parse("2026-09-24T12:00:00Z"),
                Instant.parse("2026-09-24T12:00:00Z")
        );
    }

    @Test
    void shouldCreateProject() throws Exception {
        when(projectService.create(any(CreateProjectRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/projects/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.code").value("PLATFORM"));
    }

    @Test
    void shouldGetProjectById() throws Exception {
        when(projectService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Platform"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldReturnPageAndTotalCountHeader() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id"));
        when(projectService.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(response), pageable, 1));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void shouldUpdateProject() throws Exception {
        when(projectService.update(any(Long.class), any(UpdateProjectRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PLATFORM"));
    }

    @Test
    void shouldDeleteProject() throws Exception {
        doNothing().when(projectService).delete(1L);

        mockMvc.perform(delete("/api/projects/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(projectService).delete(1L);
    }

    @Test
    void shouldReturnValidationErrors() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "code": "1-invalid",
                                  "status": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/projects"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    @Test
    void shouldReturnNotFoundError() throws Exception {
        when(projectService.findById(42L)).thenThrow(new ResourceNotFoundException("Project", 42L));

        mockMvc.perform(get("/api/projects/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Project with id '42' was not found"));
    }

    @Test
    void shouldReturnConflictError() throws Exception {
        when(projectService.create(any(CreateProjectRequest.class)))
                .thenThrow(new ConflictException("Project already exists"));

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Project already exists"));
    }

    @Test
    void shouldReturnBadRequestForMalformedStatus() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Platform",
                                  "code": "PLATFORM",
                                  "status": "UNKNOWN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is malformed"));
    }

    private String validRequest() {
        return """
                {
                  "name": "Platform",
                  "code": "PLATFORM",
                  "description": "Main platform",
                  "status": "ACTIVE"
                }
                """;
    }
}
