package itmo.user;

import itmo.user.dto.CreateUserRequest;
import itmo.user.dto.UpdateUserRequest;
import itmo.user.dto.UserResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.file.name=target/user-it.log"
)
class UserIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Validator validator;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void shouldApplyLiquibaseChangelog() {
        Integer changesetCount = jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM databasechangelog
                        WHERE id = '001-create-users' AND author = 'vs-lab1'
                        """,
                Integer.class
        );

        assertThat(changesetCount).isEqualTo(1);
    }

    @Test
    void shouldPerformUserCrudThroughHttp() {
        var createResponse = restTemplate.postForEntity(
                "/api/v1/users",
                new CreateUserRequest("Alice", "Alice@Example.COM", UserRole.DEVELOPER),
                UserResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getHeaders().getLocation()).isNotNull();
        UserResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.email()).isEqualTo("alice@example.com");

        var getResponse = restTemplate.getForEntity(
                "/api/v1/users/{id}",
                UserResponse.class,
                created.id()
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isEqualTo(created);

        var listResponse = restTemplate.getForEntity(
                "/api/v1/users?page=0&size=20",
                UserResponse[].class
        );
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        assertThat(listResponse.getBody()).hasSize(1);

        var updateResponse = restTemplate.exchange(
                "/api/v1/users/{id}",
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateUserRequest(
                        "Alice Lead",
                        "lead@example.com",
                        UserRole.TEAM_LEAD
                )),
                UserResponse.class,
                created.id()
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).isNotNull();
        assertThat(updateResponse.getBody().role()).isEqualTo(UserRole.TEAM_LEAD);

        var deleteResponse = restTemplate.exchange(
                "/api/v1/users/{id}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class,
                created.id()
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.getForEntity(
                "/api/v1/users/{id}",
                String.class,
                created.id()
        ).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldRejectDuplicateEmailIgnoringCase() {
        var firstResponse = restTemplate.postForEntity(
                "/api/v1/users",
                new CreateUserRequest("Alice", "Alice@Example.COM", UserRole.DEVELOPER),
                UserResponse.class
        );
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var duplicateResponse = restTemplate.postForEntity(
                "/api/v1/users",
                new CreateUserRequest("Another Alice", "alice@example.com", UserRole.ADMIN),
                String.class
        );
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateResponse.getBody()).contains("already exists");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldValidateUserEntity() {
        User invalidUser = new User(" ", "invalid-email", null);

        Set<String> invalidFields = validator.validate(invalidUser).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(invalidFields).containsExactlyInAnyOrder("name", "email", "role");
    }

    @Test
    void shouldExposeUserApiInOpenApiDocument() {
        var invalidPage = restTemplate.getForEntity("/api/v1/users?size=51", String.class);
        assertThat(invalidPage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        var response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("/api/v1/users")
                .contains("X-Total-Count")
                .contains("ApiError");
    }
}
