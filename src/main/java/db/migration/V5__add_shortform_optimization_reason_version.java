package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Adds an internal version marker for one-time optimization reason refreshes. */
public class V5__add_shortform_optimization_reason_version extends BaseJavaMigration {

    private static final String TABLE = "shortform_analyses";
    private static final String COLUMN = "optimization_reason_version";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!tableExists(connection, TABLE) || columnExists(connection, TABLE, COLUMN)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `shortform_analyses` ADD COLUMN `optimization_reason_version` VARCHAR(20) NULL");
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
