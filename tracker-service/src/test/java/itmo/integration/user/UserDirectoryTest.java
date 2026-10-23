package itmo.integration.user;

import feign.FeignException;
import feign.Request;
import itmo.common.exception.ConflictException;
import itmo.common.exception.DependencyUnavailableException;
import itmo.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDirectoryTest {
    @Mock
    private UserClient client;

    @Test
    void shouldAcceptTeamLead() {
        when(client.resolve(new ResolveUsersRequest(Set.of(1L))))
                .thenReturn(List.of(new UserReference(1L, "TEAM_LEAD")));
        assertThatCode(() -> new UserDirectory(client).requireTeamLead(1L)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectWrongRole() {
        when(client.resolve(new ResolveUsersRequest(Set.of(1L))))
                .thenReturn(List.of(new UserReference(1L, "DEVELOPER")));
        assertThatThrownBy(() -> new UserDirectory(client).requireTeamLead(1L))
                .isInstanceOf(ConflictException.class).hasMessageContaining("not a team lead");
    }

    @Test
    void shouldReportMissingOrDeletedUserFromSuccessfulBatch() {
        when(client.resolve(new ResolveUsersRequest(Set.of(1L, 2L))))
                .thenReturn(List.of(new UserReference(1L, "ADMIN")));
        assertThatThrownBy(() -> new UserDirectory(client).requireUsers(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("'2'");
    }

    @Test
    void shouldTranslateCircuitBreakerRejection() {
        var failure = new NoFallbackAvailableException("No fallback", new IllegalStateException("Circuit open"));
        when(client.resolve(new ResolveUsersRequest(Set.of(1L)))).thenThrow(failure);
        assertThatThrownBy(() -> new UserDirectory(client).requireUsers(1L))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessage("User service is unavailable").hasCause(failure);
    }

    @Test
    void shouldTranslateTransportFailureInsteadOfAcceptingUnverifiedUsers() {
        Request request = Request.create(Request.HttpMethod.POST, "/internal/users/resolve", Map.of(),
                (byte[]) null, StandardCharsets.UTF_8, null);
        when(client.resolve(new ResolveUsersRequest(Set.of(1L))))
                .thenThrow(new FeignException.ServiceUnavailable("Unavailable", request, null, Map.of()));
        assertThatThrownBy(() -> new UserDirectory(client).requireUsers(1L))
                .isInstanceOf(DependencyUnavailableException.class);
    }
}
