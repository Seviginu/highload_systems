package itmo.user.mapper;

import itmo.user.dto.CreateUserRequest;
import itmo.user.dto.UpdateUserRequest;
import itmo.user.entity.User;
import itmo.user.entity.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    void shouldNormalizeUserWhenMappingCreateRequest() {
        CreateUserRequest request = new CreateUserRequest(
                "  Alice  ",
                "  Alice@Example.COM ",
                UserRole.DEVELOPER
        );

        User user = mapper.toEntity(request);

        assertThat(user.getName()).isEqualTo("Alice");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getRole()).isEqualTo(UserRole.DEVELOPER);
    }

    @Test
    void shouldUpdateExistingUser() {
        User user = new User("Old", "old@example.com", UserRole.DEVELOPER);
        UpdateUserRequest request = new UpdateUserRequest(
                " Team Lead ",
                "LEAD@EXAMPLE.COM",
                UserRole.TEAM_LEAD
        );

        mapper.updateEntity(user, request);

        assertThat(user.getName()).isEqualTo("Team Lead");
        assertThat(user.getEmail()).isEqualTo("lead@example.com");
        assertThat(user.getRole()).isEqualTo(UserRole.TEAM_LEAD);
    }
}

