package itmo.project.mapper;

import itmo.project.entity.Project;
import itmo.project.entity.ProjectMember;
import itmo.project.entity.ProjectStatus;
import itmo.user.entity.User;
import itmo.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectMemberMapperTest {

    private final ProjectMemberMapper mapper = new ProjectMemberMapper();

    @Test
    void shouldMapProjectMember() {
        Project project = new Project("Platform", "PLATFORM", null, ProjectStatus.ACTIVE);
        User user = new User("Alice", "alice@example.com", UserRole.DEVELOPER);
        ProjectMember member = new ProjectMember(project, user);
        Instant joinedAt = Instant.parse("2026-09-25T00:00:00Z");
        ReflectionTestUtils.setField(project, "id", 10L);
        ReflectionTestUtils.setField(user, "id", 20L);
        ReflectionTestUtils.setField(member, "id", 30L);
        ReflectionTestUtils.setField(member, "joinedAt", joinedAt);

        var response = mapper.toResponse(member);

        assertThat(response.id()).isEqualTo(30L);
        assertThat(response.projectId()).isEqualTo(10L);
        assertThat(response.userId()).isEqualTo(20L);
        assertThat(response.joinedAt()).isEqualTo(joinedAt);
        assertThat(response.active()).isTrue();
    }
}
