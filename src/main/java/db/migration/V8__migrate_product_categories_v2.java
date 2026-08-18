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
 * Converts product category values from the previous 9-value enum to the new 10-value enum
 * before Hibernate applies the current {@code ProductCategory} definition.
 *
 * <p>{@code SERUM} and {@code ESSENCE} are merged into {@code ESSENCE_SERUM}. {@code SUNCREAM},
 * {@code CLEANSER}, and {@code MASK} no longer exist and fall back to {@code ETC}. The column is
 * temporarily widened to VARCHAR so the legacy native MySQL ENUM values can be converted, then the
 * ENUM definition is restored with the new value set.</p>
 */
public class V8__migrate_product_categories_v2 extends BaseJavaMigration {

    private static final String PRODUCTS = "products";
    private static final String CATEGORY = "category";
    private static final String MYSQL_PRODUCT_CATEGORY_ENUM = """
            ENUM('BAM','CREAM','ESSENCE_SERUM','ETC','EYECARE','FACEOIL','LOTION','MIST','SKIN_TONER','SKIN_TONERPAD')
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
                SET `category` = 'ESSENCE_SERUM'
                WHERE `category` IN ('SERUM', 'ESSENCE')
                """);
        execute(connection, """
                UPDATE `products`
                SET `category` = 'ETC'
                WHERE `category` IN ('SUNCREAM', 'CLEANSER', 'MASK')
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
