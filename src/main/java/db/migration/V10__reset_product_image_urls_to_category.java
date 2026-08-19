package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Resets every {@code products.image_url} to the static category-image path (or {@code NULL} when
 * the product has no category), discarding any URL sourced from the now-removed Kakao image search.
 *
 * <p>Product master rows created before the category/image refactor can still hold a raw Kakao
 * image URL. Some of those URLs embed non-percent-encoded characters (e.g. Korean text copied
 * verbatim from the source page), and clients that forward the URL as an HTTP header (image proxy
 * requests, etc.) fail with "String contains non ISO-8859-1 code point." Every image now comes from
 * {@code CategoryImageResolver} ({@code /images/categories/<category>.png}), so this migration makes
 * every existing row consistent with that going forward.</p>
 */
public class V10__reset_product_image_urls_to_category extends BaseJavaMigration {

    private static final String PRODUCTS = "products";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!tableExists(connection, PRODUCTS)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    UPDATE `products`
                    SET `image_url` = CASE
                        WHEN `category` IS NULL THEN NULL
                        ELSE CONCAT('/images/categories/', LOWER(`category`), '.png')
                    END
                    """);
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getTables(connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            return result.next();
        }
    }
}
