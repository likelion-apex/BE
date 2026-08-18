package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 인벤토리 성분/맞춤 분석 캐시 테이블. Hibernate ddl-auto에만 맡기면 운영에서 테이블이 없어 COMMON-500이 난다.
 */
public class V6__create_inventory_ai_caches extends BaseJavaMigration {

    private static final String TABLE = "inventory_ai_caches";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (tableExists(connection, TABLE)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE `inventory_ai_caches` (
                        `cache_key` VARCHAR(255) NOT NULL,
                        `payload` TEXT NOT NULL,
                        `expires_at` DATETIME NOT NULL,
                        PRIMARY KEY (`cache_key`)
                    )
                    """);
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
}
