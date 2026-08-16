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
 * Converts legacy product category values before Hibernate applies the current enum definition.
 *
 * <p>The production column was created as a native MySQL ENUM. The legacy combined category
 * {@code ESSENCE_AMPOULE_SERUM} and other removed values therefore have to be converted while the
 * column is temporarily widened to VARCHAR. Otherwise Hibernate cannot replace the ENUM definition
 * and existing rows cannot be read by {@code ProductCategory}.</p>
 */
public class V2__migrate_product_categories extends BaseJavaMigration {

    private static final String PRODUCTS = "products";
    private static final String CATEGORY = "category";
    private static final String MYSQL_PRODUCT_CATEGORY_ENUM = """
            ENUM('CLEANSER','CREAM','ESSENCE','ETC','LOTION','MASK','SERUM','SKIN_TONER','SUNCREAM')
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
                SET `category` = 'ESSENCE'
                WHERE `category` = 'ESSENCE_AMPOULE_SERUM'
                  AND (`name` LIKE '%에센스%' OR LOWER(`name`) LIKE '%essence%')
                """);
        execute(connection, """
                UPDATE `products`
                SET `category` = 'SERUM'
                WHERE `category` = 'ESSENCE_AMPOULE_SERUM'
                """);
        execute(connection, """
                UPDATE `products`
                SET `category` = 'LOTION'
                WHERE `category` = 'LOTION_EMULSION'
                """);
        execute(connection, """
                UPDATE `products`
                SET `category` = 'SKIN_TONER'
                WHERE `category` = 'SKIN_TONER_PAD'
                """);
        execute(connection, """
                UPDATE `products`
                SET `category` = 'ETC'
                WHERE `category` IN ('FACE_OIL', 'EYE_CARE', 'MIST_GEL', 'BALM_MULTIBALM')
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
