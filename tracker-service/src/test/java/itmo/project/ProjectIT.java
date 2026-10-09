package itmo.project;

import itmo.project.dto.AddProjectMemberRequest;
import itmo.project.dto.CreateProjectRequest;
import itmo.project.dto.ProjectMemberResponse;
import itmo.project.dto.ProjectResponse;
import itmo.project.dto.UpdateProjectRequest;
import itmo.project.entity.Project;
import itmo.project.entity.ProjectStatus;
import itmo.project.repository.ProjectMemberRepository;
import itmo.project.repository.ProjectRepository;
import itmo.user.entity.User;
import itmo.user.entity.UserRole;
import itmo.user.repository.UserRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.file.name=target/project-it.log"
)
class ProjectIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Validator validator;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM task_labels");
        jdbcTemplate.update("DELETE FROM tasks");
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldApplyProjectLiquibaseChangeset() {
        Integer changesetCount = jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM databasechangelog
                        WHERE id = '002-create-projects' AND author = 'vs-lab1'
                        """,
                Integer.class
        );

        assertThat(changesetCount).isEqualTo(1);
    }

    @Test
    void shouldPerformProjectCrudThroughHttp() {
        Long teamLeadId = createTeamLead().getId();
        var createResponse = restTemplate.postForEntity(
                "/api/v1/projects",
                new CreateProjectRequest(
                        "Platform",
                        "platform",
                        "Main platform",
                        ProjectStatus.PLANNED,
                        teamLeadId
                ),
                ProjectResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getHeaders().getLocation()).isNotNull();
        ProjectResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.code()).isEqualTo("PLATFORM");

        var getResponse = restTemplate.getForEntity(
                "/api/v1/projects/{id}",
                ProjectResponse.class,
                created.id()
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isEqualTo(created);

        var listResponse = restTemplate.getForEntity(
                "/api/v1/projects?page=0&size=20",
                ProjectResponse[].class
        );
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        assertThat(listResponse.getBody()).hasSize(1);

        var updateResponse = restTemplate.exchange(
                "/api/v1/projects/{id}",
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateProjectRequest(
                        "Platform Core",
                        "platform_core",
                        "Core services",
                        ProjectStatus.ACTIVE
                )),
                ProjectResponse.class,
                created.id()
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).isNotNull();
        assertThat(updateResponse.getBody().code()).isEqualTo("PLATFORM_CORE");
        assertThat(updateResponse.getBody().status()).isEqualTo(ProjectStatus.ACTIVE);

        var deleteResponse = restTemplate.exchange(
                "/api/v1/projects/{id}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class,
                created.id()
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.getForEntity(
                "/api/v1/projects/{id}",
                String.class,
                created.id()
        ).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldRejectDuplicateNormalizedCode() {
        Long teamLeadId = createTeamLead().getId();
        var firstResponse = restTemplate.postForEntity(
                "/api/v1/projects",
                new CreateProjectRequest("Platform", "platform", null, ProjectStatus.ACTIVE, teamLeadId),
                ProjectResponse.class
        );
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var duplicateResponse = restTemplate.postForEntity(
                "/api/v1/projects",
                new CreateProjectRequest("Another", "PLATFORM", null, ProjectStatus.PLANNED, teamLeadId),
                String.class
        );
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateResponse.getBody()).contains("already exists");
        assertThat(projectRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldManageProjectMembersThroughHttp() {
        User teamLead = createTeamLead();
        User developer = userRepository.saveAndFlush(
                new User("Developer", "developer@example.com", UserRole.DEVELOPER)
        );
        ProjectResponse project = restTemplate.postForEntity(
                "/api/v1/projects",
                new CreateProjectRequest(
                        "Platform",
                        "PLATFORM",
                        null,
                        ProjectStatus.ACTIVE,
                        teamLead.getId()
                ),
                ProjectResponse.class
        ).getBody();
        assertThat(project).isNotNull();

        var addResponse = restTemplate.postForEntity(
                "/api/v1/projects/{projectId}/members",
                new AddProjectMemberRequest(developer.getId()),
                ProjectMemberResponse.class,
                project.id()
        );

        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ProjectMemberResponse addedMember = addResponse.getBody();
        assertThat(addedMember).isNotNull();
        assertThat(addedMember.userId()).isEqualTo(developer.getId());
        assertThat(addedMember.active()).isTrue();

        var listResponse = restTemplate.getForEntity(
                "/api/v1/projects/{projectId}/members?page=0&size=20",
                ProjectMemberResponse[].class,
                project.id()
        );
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getHeaders().getFirst("X-Total-Count")).isEqualTo("2");
        assertThat(listResponse.getBody()).extracting(ProjectMemberResponse::userId)
                .containsExactlyInAnyOrder(teamLead.getId(), developer.getId());

        var invalidPageResponse = restTemplate.getForEntity(
                "/api/v1/projects/{projectId}/members?size=51",
                String.class,
                project.id()
        );
        assertThat(invalidPageResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        var deactivateResponse = restTemplate.exchange(
                "/api/v1/projects/{projectId}/members/{memberId}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class,
                project.id(),
                addedMember.id()
        );
        assertThat(deactivateResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(projectMemberRepository.findById(addedMember.id()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void shouldRollbackProjectWhenTeamLeadAssignmentFails() {
        var response = restTemplate.postForEntity(
                "/api/v1/projects",
                new CreateProjectRequest(
                        "Platform",
                        "PLATFORM",
                        null,
                        ProjectStatus.ACTIVE,
                        999_999L
                ),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(projectRepository.count()).isZero();
        assertThat(projectMemberRepository.count()).isZero();
    }

    @Test
    void shouldRejectDeletingProjectReferencedByTasks() {
        User teamLead = createTeamLead();
        ProjectResponse project = restTemplate.postForEntity(
                "/api/v1/projects",
                new CreateProjectRequest("Platform", "PLATFORM", null, ProjectStatus.ACTIVE, teamLead.getId()),
                ProjectResponse.class
        ).getBody();
        assertThat(project).isNotNull();
        jdbcTemplate.update(
                """
                        INSERT INTO tasks (task_key, title, status, priority, author_id, project_id)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                "PLATFORM-1", "Task", "TODO", "MEDIUM", teamLead.getId(), project.id()
        );

        var response = restTemplate.exchange(
                "/api/v1/projects/{id}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                String.class,
                project.id()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("referenced by tasks");
        assertThat(projectRepository.existsById(project.id())).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tasks", Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldRejectPageSizeAboveFifty() {
        var response = restTemplate.getForEntity("/api/v1/projects?size=51", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Request validation failed");
    }

    @Test
    void shouldValidateProjectEntity() {
        Project invalidProject = new Project(" ", "1-invalid", null, null);

        Set<String> invalidFields = validator.validate(invalidProject).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(invalidFields).containsExactlyInAnyOrder("name", "code", "status");
    }

    @Test
    void shouldExposeProjectApiInOpenApiDocument() {
        var response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("/api/v1/projects")
                .contains("/api/v1/projects/{projectId}/members")
                .contains("X-Total-Count")
                .contains("ProjectResponse")
                .contains("ProjectMemberResponse")
                .contains("ApiError");
    }

    private User createTeamLead() {
        return userRepository.saveAndFlush(new User("Team Lead", "lead@example.com", UserRole.TEAM_LEAD));
    }
}
