package itmo.tracker.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import itmo.tracker.application.model.AddProjectMemberCommand;
import itmo.tracker.application.model.PageQuery;
import itmo.tracker.application.model.ProjectMemberView;
import itmo.tracker.application.model.ResultPage;
import itmo.tracker.application.port.in.ProjectMemberService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class ProjectMemberControllerTest {

    @Mock private ProjectMemberService memberService;

    private WebTestClient client;
    private ProjectMemberView response;

    @BeforeEach
    void setUp() {
        client =
                WebTestClient.bindToController(
                                new ProjectMemberController(
                                        memberService,
                                        new BlockingOperations(Schedulers.boundedElastic())))
                        .webFilter(itmo.support.TestActors.context())
                        .controllerAdvice(new GlobalExceptionHandler())
                        .configureClient()
                        .defaultHeader("Authorization", "Bearer test-token")
                        .build();
        response = new ProjectMemberView(3L, 1L, 2L, Instant.parse("2026-09-25T00:00:00Z"), true);
    }

    @Test
    void shouldAddMember() throws Exception {
        when(memberService.add(
                        any(Long.class),
                        any(AddProjectMemberCommand.class),
                        org.mockito.ArgumentMatchers.any(
                                itmo.tracker.application.model.Actor.class)))
                .thenReturn(response);

        client.post()
                .uri("http://localhost/api/v1/projects/1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                        """
                        {"userId": 2}
                        """)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueEquals("Location", "http://localhost/api/v1/projects/1/members/3")
                .expectBody()
                .jsonPath("$.userId")
                .isEqualTo(2)
                .jsonPath("$.active")
                .isEqualTo(true);
    }

    @Test
    void shouldReturnMemberPage() throws Exception {
        PageQuery pageable = PageQuery.of(0, 20);
        when(memberService.findAll(1L, pageable))
                .thenReturn(new ResultPage<>(List.of(response), 1));

        client.get()
                .uri("/api/v1/projects/1/members")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-Total-Count", "1")
                .expectBody()
                .jsonPath("$[0].id")
                .isEqualTo(3);
    }

    @Test
    void shouldDeactivateMember() throws Exception {
        client.delete().uri("/api/v1/projects/1/members/3").exchange().expectStatus().isNoContent();

        verify(memberService).deactivate(1L, 3L, itmo.support.TestActors.admin());
    }

    @Test
    void shouldValidateMemberRequest() throws Exception {
        client.post()
                .uri("http://localhost/api/v1/projects/1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                        """
                        {"userId": 0}
                        """)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.fieldErrors[0].field")
                .isEqualTo("userId");
    }
}
