package itmo.project.controller;

import itmo.common.web.GlobalExceptionHandler;
import itmo.project.dto.AddProjectMemberRequest;
import itmo.project.dto.ProjectMemberResponse;
import itmo.project.service.ProjectMemberService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProjectMemberControllerTest {

    @Mock
    private ProjectMemberService memberService;

    private MockMvc mockMvc;
    private ProjectMemberResponse response;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProjectMemberController(memberService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        response = new ProjectMemberResponse(
                3L,
                1L,
                2L,
                Instant.parse("2026-09-25T00:00:00Z"),
                true
        );
    }

    @Test
    void shouldAddMember() throws Exception {
        when(memberService.add(any(Long.class), any(AddProjectMemberRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/projects/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": 2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/projects/1/members/3"))
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldReturnMemberPage() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id"));
        when(memberService.findAll(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(response), pageable, 1));

        mockMvc.perform(get("/api/v1/projects/1/members"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].id").value(3));
    }

    @Test
    void shouldDeactivateMember() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/1/members/3"))
                .andExpect(status().isNoContent());

        verify(memberService).deactivate(1L, 3L);
    }

    @Test
    void shouldValidateMemberRequest() throws Exception {
        mockMvc.perform(post("/api/v1/projects/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("userId"));
    }
}
