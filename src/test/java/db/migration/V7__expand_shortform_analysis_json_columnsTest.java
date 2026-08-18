package db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class V7__expand_shortform_analysis_json_columnsTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:shortform_json_capacity;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("""
                    CREATE TABLE shortform_analyses (
                        id BIGINT PRIMARY KEY,
                        result_json VARCHAR(65535) NULL,
                        optimization_json VARCHAR(65535) NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO shortform_analyses (id, result_json, optimization_json)
                    VALUES (1, '{"legacy":true}', '{"legacyOptimization":true}')
                    """);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void expandsBothJsonColumnsAndPreservesLargePayloadsIdempotently() throws Exception {
        V7__expand_shortform_analysis_json_columns migration =
                new V7__expand_shortform_analysis_json_columns();

        migration.migrate(context());
        assertThat(readColumn("result_json")).isEqualTo("{\"legacy\":true}");
        assertThat(readColumn("optimization_json")).isEqualTo("{\"legacyOptimization\":true}");

        String largePayload = "한".repeat(70_000);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE shortform_analyses
                SET result_json = ?, optimization_json = ?
                WHERE id = 1
                """)) {
            statement.setString(1, largePayload);
            statement.setString(2, largePayload);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }

        migration.migrate(context());

        assertThat(readColumn("result_json")).isEqualTo(largePayload);
        assertThat(readColumn("optimization_json")).isEqualTo(largePayload);
    }

    private Context context() {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        return context;
    }

    private String readColumn(String column) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT " + column + " FROM shortform_analyses WHERE id = 1")) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
