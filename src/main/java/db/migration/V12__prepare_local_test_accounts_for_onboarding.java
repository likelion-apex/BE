package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Removes prefilled nicknames from the six shared accounts so onboarding collects them. */
public class V12__prepare_local_test_accounts_for_onboarding extends BaseJavaMigration {

    private static final String TABLE = "members";
    private static final String NICKNAME = "nickname";
    private static final String ONBOARDING_COMPLETED_AT = "onboarding_completed_at";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!tableExists(connection, TABLE) || !columnExists(connection, TABLE, NICKNAME)) {
            return;
        }

        makeNicknameNullable(connection);
        clearOnlySeededNicknames(connection);
    }

    private void makeNicknameNullable(Connection connection) throws SQLException {
        if (isColumnNullable(connection, TABLE, NICKNAME)) {
            return;
        }

        if (isMySqlFamily(connection)) {
            execute(connection, "ALTER TABLE `members` MODIFY COLUMN `nickname` VARCHAR(255) NULL");
            return;
        }
        execute(connection, "ALTER TABLE `members` ALTER COLUMN `nickname` DROP NOT NULL");
    }

    private void clearOnlySeededNicknames(Connection connection) throws SQLException {
        String completionAssignment = columnExists(connection, TABLE, ONBOARDING_COMPLETED_AT)
                ? ", `onboarding_completed_at` = NULL"
                : "";
        execute(connection, """
                UPDATE `members`
                SET `nickname` = NULL%s
                WHERE `provider` = 'LOCAL'
                  AND (
                    (`provider_id` = 'soak_judge' AND `nickname` = '김멋사')
                    OR (`provider_id` = 'soak_test01' AND `nickname` = '이멋사')
                    OR (`provider_id` = 'soak_test02' AND `nickname` = '박멋사')
                    OR (`provider_id` = 'soak_test03' AND `nickname` = '최멋사')
                    OR (`provider_id` = 'soak_test04' AND `nickname` = '정멋사')
                    OR (`provider_id` = 'soak_test05' AND `nickname` = '한멋사')
                  )
                """.formatted(completionAssignment));
    }

    private boolean isMySqlFamily(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        return productName.contains("mysql") || productName.contains("mariadb");
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

    private boolean isColumnNullable(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getColumns(connection.getCatalog(), null, table, column)) {
            if (result.next()) {
                return result.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
            }
        }
        try (ResultSet result = metadata.getColumns(
                connection.getCatalog(), null, table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT))) {
            return result.next() && result.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
