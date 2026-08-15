package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Hibernate ddl-auto=update가 제거하지 못한 Routine 구 제약과 nullable 변경을 정리한다.
 *
 * <p>기존 운영 스키마에만 필요한 보정이므로 대상 테이블이나 컬럼이 없는 신규 DB에서는
 * 가능한 작업만 수행하고, 나머지 테이블 생성은 Hibernate 엔티티 매핑에 맡긴다.</p>
 */
public class V1__align_routine_schema extends BaseJavaMigration {

    private static final String ROUTINES = "routines";
    private static final String ROUTINE_LOGS = "routine_logs";
    private static final String DAILY_CONDITIONS = "daily_conditions";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        migrateDailyConditionColumn(connection);
        migrateRoutineConstraints(connection);
        migrateRoutineLogConstraints(connection);
    }

    private void migrateDailyConditionColumn(Connection connection) throws SQLException {
        if (!tableExists(connection, DAILY_CONDITIONS)
                || !columnExists(connection, DAILY_CONDITIONS, "condition")
                || columnExists(connection, DAILY_CONDITIONS, "condition_type")) {
            return;
        }

        execute(connection, """
                ALTER TABLE `daily_conditions`
                CHANGE COLUMN `condition` `condition_type` VARCHAR(20) NOT NULL
                """);
    }

    private void migrateRoutineConstraints(Connection connection) throws SQLException {
        if (!tableExists(connection, ROUTINES)) {
            return;
        }

        if (columnExists(connection, ROUTINES, "routine_type")) {
            createUniqueIndexIfMissing(
                    connection,
                    ROUTINES,
                    "uk_routine_member_analysis_save_type_routine_type",
                    "`member_id`, `source_analysis_id`, `save_type`, `routine_type`"
            );
            dropIndexIfExists(connection, ROUTINES, "uk_routine_member_analysis_save_type");
        }

        if (columnExists(connection, ROUTINES, "source_analysis_id")) {
            execute(connection, """
                    ALTER TABLE `routines`
                    MODIFY COLUMN `source_analysis_id` BIGINT NULL
                    """);
        }
    }

    private void migrateRoutineLogConstraints(Connection connection) throws SQLException {
        if (!tableExists(connection, ROUTINE_LOGS)) {
            return;
        }

        createUniqueIndexIfMissing(
                connection,
                ROUTINE_LOGS,
                "uk_routine_log_member_date_routine",
                "`member_id`, `log_date`, `routine_id`"
        );
        dropIndexIfExists(connection, ROUTINE_LOGS, "uk_routine_log_member_date");
    }

    private void createUniqueIndexIfMissing(
            Connection connection,
            String table,
            String index,
            String columns
    ) throws SQLException {
        if (indexExists(connection, table, index)) {
            return;
        }
        execute(connection, "CREATE UNIQUE INDEX `" + index + "` ON `" + table + "` (" + columns + ")");
    }

    private void dropIndexIfExists(Connection connection, String table, String index) throws SQLException {
        if (!indexExists(connection, table, index)) {
            return;
        }
        execute(connection, "ALTER TABLE `" + table + "` DROP INDEX `" + index + "`");
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

    private boolean indexExists(Connection connection, String table, String index) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (result.next()) {
                String indexName = result.getString("INDEX_NAME");
                if (index.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
            return false;
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
