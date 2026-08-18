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

class V5__add_shortform_optimization_reason_versionTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:shortform_reason_version_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("CREATE TABLE shortform_analyses (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO shortform_analyses (id) VALUES (1)");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void addsNullableVersionColumnIdempotentlyWithoutMarkingLegacyRows() throws Exception {
        V5__add_shortform_optimization_reason_version migration =
                new V5__add_shortform_optimization_reason_version();

        migration.migrate(context());
        migration.migrate(context());

        assertThat(columnExists("optimization_reason_version")).isTrue();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT optimization_reason_version FROM shortform_analyses WHERE id = 1")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isNull();
        }
    }

    private Context context() {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        return context;
    }

    private boolean columnExists(String column) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getColumns(
                connection.getCatalog(), null, "shortform_analyses", column)) {
            return result.next();
        }
    }
}
