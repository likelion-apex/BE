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
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class V1__align_routine_schemaTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:routine_schema_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        );
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("""
                    CREATE TABLE routines (
                        id BIGINT PRIMARY KEY,
                        member_id BIGINT NOT NULL,
                        source_analysis_id BIGINT NOT NULL,
                        save_type VARCHAR(20) NOT NULL,
                        routine_type VARCHAR(20) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX uk_routine_member_analysis_save_type
                    ON routines (member_id, source_analysis_id, save_type)
                    """);
            statement.execute("""
                    CREATE TABLE routine_logs (
                        id BIGINT PRIMARY KEY,
                        member_id BIGINT NOT NULL,
                        log_date DATE NOT NULL,
                        routine_id BIGINT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX uk_routine_log_member_date
                    ON routine_logs (member_id, log_date)
                    """);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void alignsLegacyConstraintsAndIsIdempotent() throws Exception {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        V1__align_routine_schema migration = new V1__align_routine_schema();

        migration.migrate(context);
        migration.migrate(context);

        assertThat(uniqueIndexes("routines"))
                .contains("uk_routine_member_analysis_save_type_routine_type")
                .doesNotContain("uk_routine_member_analysis_save_type");
        assertThat(uniqueIndexes("routine_logs"))
                .contains("uk_routine_log_member_date_routine")
                .doesNotContain("uk_routine_log_member_date");
        assertThat(sourceAnalysisNullable()).isTrue();
    }

    private Set<String> uniqueIndexes(String table) throws SQLException {
        Set<String> indexes = new HashSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getIndexInfo(connection.getCatalog(), null, table, true, false)) {
            while (result.next()) {
                String index = result.getString("INDEX_NAME");
                if (index != null && !"PRIMARY_KEY".equalsIgnoreCase(index)) {
                    indexes.add(index.toLowerCase());
                }
            }
        }
        return indexes;
    }

    private boolean sourceAnalysisNullable() throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getColumns(
                connection.getCatalog(), null, "routines", "source_analysis_id")) {
            assertThat(result.next()).isTrue();
            return result.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
        }
    }
}
