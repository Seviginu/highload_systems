package itmo.project.mapper;

import itmo.project.entity.Project;
import itmo.project.entity.ProjectMember;
import itmo.project.entity.ProjectStatus;
import itmo.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectMemberMapperTest {

    private final ProjectMemberMapper mapper = new ProjectMemberMapper();

    @Test
    void shouldMapProjectMember() {
        Project project = new Project("Platform", "PLATFORM", null, ProjectStatus.ACTIVE);
        TestUser user = new TestUser("DEVELOPER");
        Instant joinedAt = Instant.parse("2026-09-25T00:00:00Z");
        ReflectionTestUtils.setField(project, "id", 10L);
        ReflectionTestUtils.setField(user, "id", 20L);
        ProjectMember member = new ProjectMember(project, user.getId());
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
