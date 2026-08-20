package db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class V12__prepare_local_test_accounts_for_onboardingTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:prepare_local_accounts;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        );
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("""
                    CREATE TABLE members (
                        id BIGINT PRIMARY KEY,
                        provider VARCHAR(20) NOT NULL,
                        provider_id VARCHAR(255) NOT NULL,
                        nickname VARCHAR(255) NOT NULL,
                        onboarding_completed_at DATETIME NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO members (id, provider, provider_id, nickname, onboarding_completed_at) VALUES
                    (1, 'LOCAL', 'soak_judge', '김멋사', CURRENT_TIMESTAMP),
                    (2, 'LOCAL', 'soak_test01', '직접입력', CURRENT_TIMESTAMP),
                    (3, 'KAKAO', 'kakao-user', '김멋사', CURRENT_TIMESTAMP),
                    (4, 'LOCAL', 'other-local', '김멋사', CURRENT_TIMESTAMP),
                    (5, 'LOCAL', 'soak_test02', '박멋사', NULL)
                    """);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void clearsOnlyUntouchedSeededNicknamesAndAllowsNullIdempotently() throws Exception {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        V12__prepare_local_test_accounts_for_onboarding migration =
                new V12__prepare_local_test_accounts_for_onboarding();

        migration.migrate(context);
        migration.migrate(context);

        assertThat(isNicknameNullable()).isTrue();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT id, nickname, onboarding_completed_at
                     FROM members
                     ORDER BY id
                     """)) {
            assertAccount(result, 1L, null, false);
            assertAccount(result, 2L, "직접입력", true);
            assertAccount(result, 3L, "김멋사", true);
            assertAccount(result, 4L, "김멋사", true);
            assertAccount(result, 5L, null, false);
            assertThat(result.next()).isFalse();
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO members (id, provider, provider_id, nickname)
                    VALUES (6, 'LOCAL', 'new-local', NULL)
                    """);
        }
    }

    private boolean isNicknameNullable() throws SQLException {
        try (ResultSet result = connection.getMetaData()
                .getColumns(connection.getCatalog(), null, "members", "nickname")) {
            return result.next() && result.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
        }
    }

    private void assertAccount(ResultSet result, long id, String nickname, boolean completed) throws SQLException {
        assertThat(result.next()).isTrue();
        assertThat(result.getLong("id")).isEqualTo(id);
        assertThat(result.getString("nickname")).isEqualTo(nickname);
        assertThat(result.getTimestamp("onboarding_completed_at") != null).isEqualTo(completed);
    }
}
