package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Adds optional local credentials and an explicit onboarding completion marker to members. */
public class V11__add_local_login_support extends BaseJavaMigration {

    private static final String TABLE = "members";
    private static final String PASSWORD_HASH = "password_hash";
    private static final String ONBOARDING_COMPLETED_AT = "onboarding_completed_at";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!tableExists(connection, TABLE)) {
            return;
        }

        if (!columnExists(connection, TABLE, PASSWORD_HASH)) {
            execute(connection, "ALTER TABLE `members` ADD COLUMN `password_hash` VARCHAR(100) NULL");
        }
        if (!columnExists(connection, TABLE, ONBOARDING_COMPLETED_AT)) {
            execute(connection, "ALTER TABLE `members` ADD COLUMN `onboarding_completed_at` DATETIME NULL");
        }

        execute(connection, """
                UPDATE `members`
                SET `onboarding_completed_at` = CURRENT_TIMESTAMP
                WHERE `provider` = 'KAKAO'
                  AND `onboarding_completed_at` IS NULL
                """);
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
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
