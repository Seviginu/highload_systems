package itmo.user.domain;

import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.assertj.core.api.Assertions.assertThat;

class UserTest {
    @Test
    void shouldNormalizeUserOnCreationAndUpdate() {
        var user = new User("  Alice  ", "  Alice@Example.COM ", UserRole.DEVELOPER);
        assertThat(user.getName()).isEqualTo("Alice");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        user.update(" Team Lead ", "LEAD@EXAMPLE.COM", UserRole.TEAM_LEAD);
        assertThat(user.getName()).isEqualTo("Team Lead");
        assertThat(user.getEmail()).isEqualTo("lead@example.com");
        assertThat(user.getRole()).isEqualTo(UserRole.TEAM_LEAD);
    }
    @Test
    void shouldNormalizeEmailIndependentlyOfDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(User.normalizeEmail("INFO@EXAMPLE.COM")).isEqualTo("info@example.com");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
