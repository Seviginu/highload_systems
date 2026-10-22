package itmo.user;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class UserMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void shouldUpgradeLab2SchemaWithoutChangingExistingUsers() throws Exception {
        migrate("db/changelog/001-create-users.sql");
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, name, email, role, version)
                    VALUES (42, 'Existing', 'existing@example.com', 'TEAM_LEAD', 7)
                    """);
        }
        migrate("db/changelog/db.changelog-master.yaml");
        migrate("db/changelog/db.changelog-master.yaml");
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var row = statement.executeQuery("SELECT * FROM users WHERE id = 42")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getString("name")).isEqualTo("Existing");
            assertThat(row.getString("email")).isEqualTo("existing@example.com");
            assertThat(row.getString("role")).isEqualTo("TEAM_LEAD");
            assertThat(row.getLong("version")).isEqualTo(7);
            assertThat(row.getString("password_hash")).isNull();
            assertThat(row.getBoolean("deleted")).isFalse();
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE users SET password_hash = 'plaintext' WHERE id = 42"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("ck_users_password_hash");
        }
    }

    private void migrate(String changelog) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var liquibase = new Liquibase(changelog, new ClassLoaderResourceAccessor(), new JdbcConnection(connection))) {
            liquibase.update(new Contexts());
        }
    }
}
