package itmo.project;

import itmo.project.dto.CreateProjectRequest;
import itmo.project.dto.ProjectResponse;
import itmo.project.dto.UpdateProjectRequest;
import itmo.project.entity.Project;
import itmo.project.entity.ProjectStatus;
import itmo.project.repository.ProjectRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Validator validator;

    @BeforeEach
    void cleanDatabase() {
        projectRepository.deleteAll();
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
        var createResponse = restTemplate.postForEntity(
                "/api/projects",
                new CreateProjectRequest(
                        "Platform",
                        "platform",
                        "Main platform",
                        ProjectStatus.PLANNED
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
                "/api/projects/{id}",
                ProjectResponse.class,
                created.id()
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isEqualTo(created);

        var listResponse = restTemplate.getForEntity(
                "/api/projects?page=0&size=20",
                ProjectResponse[].class
        );
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        assertThat(listResponse.getBody()).hasSize(1);

        var updateResponse = restTemplate.exchange(
                "/api/projects/{id}",
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
                "/api/projects/{id}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class,
                created.id()
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.getForEntity(
                "/api/projects/{id}",
                String.class,
                created.id()
        ).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldRejectDuplicateNormalizedCode() {
        var firstResponse = restTemplate.postForEntity(
                "/api/projects",
                new CreateProjectRequest("Platform", "platform", null, ProjectStatus.ACTIVE),
                ProjectResponse.class
        );
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var duplicateResponse = restTemplate.postForEntity(
                "/api/projects",
                new CreateProjectRequest("Another", "PLATFORM", null, ProjectStatus.PLANNED),
                String.class
        );
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateResponse.getBody()).contains("already exists");
        assertThat(projectRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectPageSizeAboveFifty() {
        var response = restTemplate.getForEntity("/api/projects?size=51", String.class);

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
                .contains("/api/projects")
                .contains("X-Total-Count")
                .contains("ProjectResponse")
                .contains("ApiError");
    }
}
