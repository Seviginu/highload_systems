package itmo.security;

import itmo.infrastructure.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import reactor.test.StepVerifier;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserTest {
    @Test
    void shouldRejectMissingSecurityContext() {
        StepVerifier.create(CurrentUser.current()).expectError(AccessDeniedException.class).verify();
    }

    @Test
    void shouldKeepRolesImmutableAndRedactAuthorization() {
        var roles = new HashSet<>(Set.of("TEAM_LEAD"));
        var actor = new CurrentUser(1, roles, "Bearer secret-token");
        roles.add("ADMIN");
        assertThat(actor.roles()).containsExactly("TEAM_LEAD");
        assertThat(actor.isAdmin()).isFalse();
        assertThat(actor.isTeamLead()).isTrue();
        assertThat(actor.toString()).doesNotContain("secret-token").contains("<redacted>");
    }
}
