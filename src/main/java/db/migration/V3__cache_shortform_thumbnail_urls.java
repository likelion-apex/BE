package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Adds a persisted YouTube thumbnail URL to short-form analyses and backfills legacy rows
 * without consuming YouTube Data API quota.
 */
public class V3__cache_shortform_thumbnail_urls extends BaseJavaMigration {

    private static final String TABLE = "shortform_analyses";
    private static final String COLUMN = "thumbnail_url";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!tableExists(connection, TABLE)) {
            return;
        }
        if (!columnExists(connection, TABLE, COLUMN)) {
            execute(connection, "ALTER TABLE `shortform_analyses` ADD COLUMN `thumbnail_url` VARCHAR(1000) NULL");
        }
        execute(connection, """
                UPDATE `shortform_analyses`
                SET `thumbnail_url` = CONCAT('https://i.ytimg.com/vi/', `video_id`, '/hqdefault.jpg')
                WHERE (`thumbnail_url` IS NULL OR TRIM(`thumbnail_url`) = '')
                  AND `video_id` IS NOT NULL
                  AND TRIM(`video_id`) <> ''
                """);
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getTables(connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            return result.next();
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getColumns(connection.getCatalog(), null, table, column)) {
            return result.next();
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
