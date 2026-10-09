package itmo.label;

import itmo.label.dto.CreateLabelRequest;
import itmo.label.dto.LabelResponse;
import itmo.label.dto.UpdateLabelRequest;
import itmo.label.entity.Label;
import itmo.label.repository.LabelRepository;
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
        properties = "logging.file.name=target/label-it.log"
)
class LabelIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Validator validator;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM task_labels");
        jdbcTemplate.update("DELETE FROM tasks");
        jdbcTemplate.update("DELETE FROM project_members");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM users");
        labelRepository.deleteAll();
    }

    @Test
    void shouldApplyLabelLiquibaseChangeset() {
        Integer changesetCount = jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM databasechangelog
                        WHERE id = '003-create-labels' AND author = 'vs-lab1'
                        """,
                Integer.class
        );

        assertThat(changesetCount).isEqualTo(1);
    }

    @Test
    void shouldPerformLabelCrudThroughHttp() {
        var createResponse = restTemplate.postForEntity(
                "/api/v1/labels",
                new CreateLabelRequest(" Backend ", "#a1b2c3"),
                LabelResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getHeaders().getLocation()).isNotNull();
        LabelResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.name()).isEqualTo("Backend");
        assertThat(created.color()).isEqualTo("#A1B2C3");

        var getResponse = restTemplate.getForEntity(
                "/api/v1/labels/{id}",
                LabelResponse.class,
                created.id()
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isEqualTo(created);

        var listResponse = restTemplate.getForEntity(
                "/api/v1/labels?page=0&size=20",
                LabelResponse[].class
        );
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        assertThat(listResponse.getBody()).hasSize(1);

        var updateResponse = restTemplate.exchange(
                "/api/v1/labels/{id}",
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateLabelRequest("API", null)),
                LabelResponse.class,
                created.id()
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).isNotNull();
        assertThat(updateResponse.getBody().name()).isEqualTo("API");
        assertThat(updateResponse.getBody().color()).isNull();

        var deleteResponse = restTemplate.exchange(
                "/api/v1/labels/{id}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class,
                created.id()
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(labelRepository.count()).isZero();
    }

    @Test
    void shouldRejectDuplicateNameIgnoringCase() {
        assertThat(restTemplate.postForEntity(
                "/api/v1/labels",
                new CreateLabelRequest("Backend", null),
                LabelResponse.class
        ).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var duplicateResponse = restTemplate.postForEntity(
                "/api/v1/labels",
                new CreateLabelRequest("backend", "#FFFFFF"),
                String.class
        );

        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateResponse.getBody()).contains("already exists");
        assertThat(labelRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectDeletingLabelReferencedByTasks() {
        Label label = labelRepository.saveAndFlush(new Label("Backend", null));
        Long userId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO users (name, email, role)
                        VALUES ('Author', 'author@example.com', 'TEAM_LEAD')
                        RETURNING id
                        """,
                Long.class
        );
        Long projectId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO projects (name, code, status)
                        VALUES ('Platform', 'PLATFORM', 'ACTIVE')
                        RETURNING id
                        """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO tasks (task_key, title, status, priority, author_id, project_id)
                        VALUES ('PLATFORM-1', 'Task', 'TODO', 'MEDIUM', ?, ?)
                        RETURNING id
                        """,
                Long.class,
                userId,
                projectId
        );
        jdbcTemplate.update(
                "INSERT INTO task_labels (task_id, label_id) VALUES (?, ?)",
                taskId,
                label.getId()
        );

        var response = restTemplate.exchange(
                "/api/v1/labels/{id}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                String.class,
                label.getId()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("referenced by tasks");
        assertThat(labelRepository.existsById(label.getId())).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM task_labels", Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldValidateLabelEntity() {
        Label invalidLabel = new Label(" ", "red");

        Set<String> invalidFields = validator.validate(invalidLabel).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(invalidFields).containsExactlyInAnyOrder("name", "color");
    }

    @Test
    void shouldRejectPageSizeAboveFifty() {
        var response = restTemplate.getForEntity("/api/v1/labels?size=51", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Request validation failed");
    }

    @Test
    void shouldExposeLabelApiInOpenApiDocument() {
        var response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("/api/v1/labels")
                .contains("X-Total-Count")
                .contains("LabelResponse")
                .contains("ApiError");
    }
}
