package db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class V11__add_local_login_supportTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:add_local_login_support;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        );
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("""
                    CREATE TABLE members (
                        id BIGINT PRIMARY KEY,
                        provider VARCHAR(20) NOT NULL,
                        provider_id VARCHAR(255) NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO members (id, provider, provider_id) VALUES
                    (1, 'KAKAO', 'kakao-existing'),
                    (2, 'LOCAL', 'soak_existing')
                    """);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void addsNullableColumnsAndOnlyCompletesExistingKakaoMembersIdempotently() throws Exception {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        V11__add_local_login_support migration = new V11__add_local_login_support();

        migration.migrate(context);
        migration.migrate(context);

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT provider, password_hash, onboarding_completed_at
                     FROM members
                     ORDER BY id
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("provider")).isEqualTo("KAKAO");
            assertThat(result.getString("password_hash")).isNull();
            assertThat(result.getTimestamp("onboarding_completed_at")).isNotNull();

            assertThat(result.next()).isTrue();
            assertThat(result.getString("provider")).isEqualTo("LOCAL");
            assertThat(result.getString("password_hash")).isNull();
            assertThat(result.getTimestamp("onboarding_completed_at")).isNull();
        }
    }
}
