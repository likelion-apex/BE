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
 * Removes {@code ETC} from the product category set, locking the enum to the 9 real
 * skincare-step categories. Any product left as {@code ETC} by {@code V8} (originally
 * {@code SUNCREAM}, {@code CLEANSER}, or {@code MASK}) no longer has a home in the fixed 9
 * categories, so it falls back to {@code NULL} (no category assigned) rather than a fabricated
 * bucket. The column is temporarily widened to VARCHAR so the legacy native MySQL ENUM value can
 * be converted, then the ENUM definition is restored with the new 9-value set.
 */
public class V9__remove_etc_product_category extends BaseJavaMigration {

    private static final String PRODUCTS = "products";
    private static final String CATEGORY = "category";
    private static final String MYSQL_PRODUCT_CATEGORY_ENUM = """
            ENUM('BAM','CREAM','ESSENCE_SERUM','EYECARE','FACEOIL','LOTION','MIST','SKIN_TONER','SKIN_TONERPAD')
            """.trim();

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!tableExists(connection, PRODUCTS) || !columnExists(connection, PRODUCTS, CATEGORY)) {
            return;
        }

        widenCategoryColumn(connection);
        migrateLegacyValues(connection);
        restoreMySqlEnum(connection);
    }

    private void widenCategoryColumn(Connection connection) throws SQLException {
        if (isMySqlFamily(connection)) {
            execute(connection, """
                    ALTER TABLE `products`
                    MODIFY COLUMN `category` VARCHAR(32) NULL
                    """);
            return;
        }

        execute(connection, """
                ALTER TABLE `products`
                ALTER COLUMN `category` VARCHAR(32)
                """);
    }

    private void migrateLegacyValues(Connection connection) throws SQLException {
        execute(connection, """
                UPDATE `products`
                SET `category` = NULL
                WHERE `category` = 'ETC'
                """);
    }

    private void restoreMySqlEnum(Connection connection) throws SQLException {
        if (!isMySqlFamily(connection)) {
            return;
        }
        execute(connection, "ALTER TABLE `products` MODIFY COLUMN `category` "
                + MYSQL_PRODUCT_CATEGORY_ENUM + " NULL");
    }

    private boolean isMySqlFamily(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        return productName.contains("mysql") || productName.contains("mariadb");
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
