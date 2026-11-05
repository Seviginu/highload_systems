package itmo.security;

import itmo.tracker.adapter.in.web.CurrentUser;
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
    void shouldKeepRolesImmutable() {
        var roles = new HashSet<>(Set.of("TEAM_LEAD"));
        var actor = new itmo.tracker.application.model.Actor(1, roles);
        roles.add("ADMIN");
        assertThat(actor.roles()).containsExactly("TEAM_LEAD");
        assertThat(actor.isAdmin()).isFalse();
        assertThat(actor.isTeamLead()).isTrue();
    }
}
