package db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class V3__cache_shortform_thumbnail_urlsTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:shortform_thumbnail_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        );
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("""
                    CREATE TABLE shortform_analyses (
                        id BIGINT PRIMARY KEY,
                        video_id VARCHAR(20) NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO shortform_analyses (id, video_id) VALUES
                    (1, 'firstVideo'),
                    (2, 'secondVideo')
                    """);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void addsColumnAndBackfillsLegacyRowsIdempotently() throws Exception {
        V3__cache_shortform_thumbnail_urls migration = migration();

        migration.migrate(context());
        migration.migrate(context());

        assertThat(columnExists("thumbnail_url")).isTrue();
        assertThat(thumbnailUrls()).containsExactly(
                entry(1L, "https://i.ytimg.com/vi/firstVideo/hqdefault.jpg"),
                entry(2L, "https://i.ytimg.com/vi/secondVideo/hqdefault.jpg")
        );
    }

    @Test
    void preservesAlreadyCachedThumbnailUrl() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE shortform_analyses ADD COLUMN thumbnail_url VARCHAR(1000) NULL");
            statement.execute("""
                    UPDATE shortform_analyses
                    SET thumbnail_url = 'https://img.example.test/custom.jpg'
                    WHERE id = 1
                    """);
        }

        migration().migrate(context());

        assertThat(thumbnailUrls()).containsExactly(
                entry(1L, "https://img.example.test/custom.jpg"),
                entry(2L, "https://i.ytimg.com/vi/secondVideo/hqdefault.jpg")
        );
    }

    private V3__cache_shortform_thumbnail_urls migration() {
        return new V3__cache_shortform_thumbnail_urls();
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

    private Map<Long, String> thumbnailUrls() throws SQLException {
        Map<Long, String> values = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT id, thumbnail_url FROM shortform_analyses ORDER BY id")) {
            while (result.next()) {
                values.put(result.getLong("id"), result.getString("thumbnail_url"));
            }
        }
        return values;
    }
}
