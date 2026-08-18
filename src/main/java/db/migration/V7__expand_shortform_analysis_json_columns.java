package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Expands persisted short-form result payloads beyond MySQL TEXT's 64 KiB limit. */
public class V7__expand_shortform_analysis_json_columns extends BaseJavaMigration {

    private static final String TABLE = "shortform_analyses";
    private static final List<String> JSON_COLUMNS = List.of("result_json", "optimization_json");

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!tableExists(connection, TABLE)) {
            return;
        }
        for (String column : JSON_COLUMNS) {
            if (columnExists(connection, TABLE, column)) {
                expandColumn(connection, column);
            }
        }
    }

    private void expandColumn(Connection connection, String column) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `" + TABLE + "` MODIFY COLUMN `" + column + "` MEDIUMTEXT NULL");
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getTables(connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            if (result.next()) {
                return true;
            }
        }
        try (ResultSet result = metadata.getTables(
                connection.getCatalog(), null, table.toUpperCase(Locale.ROOT), new String[]{"TABLE"})) {
            return result.next();
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getColumns(connection.getCatalog(), null, table, column)) {
            if (result.next()) {
                return true;
            }
        }
        try (ResultSet result = metadata.getColumns(
                connection.getCatalog(), null, table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT))) {
            return result.next();
        }
    }
}
