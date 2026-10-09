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
import org.springframework.test.web.reactive.server.WebTestClient;
import itmo.infrastructure.reactor.BlockingOperations;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMemberControllerTest {

    @Mock
    private ProjectMemberService memberService;

    private WebTestClient client;
    private ProjectMemberResponse response;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(new ProjectMemberController(memberService, new BlockingOperations(Schedulers.boundedElastic())))
                .controllerAdvice(new GlobalExceptionHandler())
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

        client.post().uri("http://localhost/api/v1/projects/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("""
                                {"userId": 2}
                                """).exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "http://localhost/api/v1/projects/1/members/3")
                .expectBody().jsonPath("$.userId").isEqualTo(2)
                .jsonPath("$.active").isEqualTo(true);
    }

    @Test
    void shouldReturnMemberPage() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id"));
        when(memberService.findAll(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(response), pageable, 1));

        client.get().uri("/api/v1/projects/1/members").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Total-Count", "1")
                .expectBody().jsonPath("$[0].id").isEqualTo(3);
    }

    @Test
    void shouldDeactivateMember() throws Exception {
        client.delete().uri("/api/v1/projects/1/members/3").exchange()
                .expectStatus().isNoContent();

        verify(memberService).deactivate(1L, 3L);
    }

    @Test
    void shouldValidateMemberRequest() throws Exception {
        client.post().uri("http://localhost/api/v1/projects/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("""
                                {"userId": 0}
                                """).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.fieldErrors[0].field").isEqualTo("userId");
    }
}
