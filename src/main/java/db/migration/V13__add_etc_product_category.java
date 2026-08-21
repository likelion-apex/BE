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
 * Restores {@code ETC} to the MySQL native ENUM for {@code products.category}, matching
 * {@code ProductCategory}. Existing 9-value rows are left unchanged; this is an additive
 * constraint change so unclassified products can be stored as {@code ETC} instead of {@code NULL}.
 * H2 (local/test) already stores the column as VARCHAR, so the ALTER is MySQL/MariaDB only.
 */
public class V13__add_etc_product_category extends BaseJavaMigration {

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
