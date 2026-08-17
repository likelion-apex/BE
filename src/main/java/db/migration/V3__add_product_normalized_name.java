package db.migration;

import domain.inventory.ProductNameNormalizer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 제품명 정규화 키를 추가하고, 같은 키를 가진 중복 상품을 하나로 합친다.
 */
public class V3__add_product_normalized_name extends BaseJavaMigration {

    private static final String PRODUCTS = "products";
    private static final String NORMALIZED_NAME = "normalized_name";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!tableExists(connection, PRODUCTS)) {
            return;
        }
        addNormalizedNameColumn(connection);
        backfillAndMerge(connection);
        enforceNotNullAndUnique(connection);
    }

    private void addNormalizedNameColumn(Connection connection) throws SQLException {
        if (columnExists(connection, PRODUCTS, NORMALIZED_NAME)) {
            return;
        }
        execute(connection, "ALTER TABLE `products` ADD COLUMN `normalized_name` VARCHAR(255) NULL");
    }

    private void backfillAndMerge(Connection connection) throws SQLException {
        List<ProductRow> rows = loadProducts(connection);
        Map<String, List<Long>> idsByKey = new LinkedHashMap<>();
        for (ProductRow row : rows) {
            String key = ProductNameNormalizer.canonicalKey(row.name);
            if (key == null || key.isBlank()) {
                key = "id" + row.id;
            }
            executeUpdate(connection,
                    "UPDATE `products` SET `normalized_name` = ? WHERE `id` = ?",
                    key, row.id);
            idsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row.id);
        }

        for (List<Long> ids : idsByKey.values()) {
            if (ids.size() < 2) {
                continue;
            }
            Long keepId = ids.stream().min(Long::compareTo).orElseThrow();
            for (Long dupId : ids) {
                if (dupId.equals(keepId)) {
                    continue;
                }
                mergeProduct(connection, keepId, dupId);
            }
        }
    }

    private void mergeProduct(Connection connection, long keepId, long dupId) throws SQLException {
        if (tableExists(connection, "inventories")) {
            reassignInventories(connection, keepId, dupId);
        }
        if (tableExists(connection, "product_ingredients")) {
            reassignProductIngredients(connection, keepId, dupId);
        }
        if (tableExists(connection, "routine_steps")) {
            executeUpdate(connection,
                    "UPDATE `routine_steps` SET `product_id` = ? WHERE `product_id` = ?",
                    keepId, dupId);
        }
        executeUpdate(connection, "DELETE FROM `products` WHERE `id` = ?", dupId);
    }

    private void reassignInventories(Connection connection, long keepId, long dupId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT `id`, `member_id` FROM `inventories` WHERE `product_id` = ?")) {
            select.setLong(1, dupId);
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    long inventoryId = result.getLong("id");
                    long memberId = result.getLong("member_id");
                    if (inventoryExists(connection, memberId, keepId)) {
                        executeUpdate(connection, "DELETE FROM `inventories` WHERE `id` = ?", inventoryId);
                    } else {
                        executeUpdate(connection,
                                "UPDATE `inventories` SET `product_id` = ? WHERE `id` = ?",
                                keepId, inventoryId);
                    }
                }
            }
        }
    }

    private boolean inventoryExists(Connection connection, long memberId, long productId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM `inventories` WHERE `member_id` = ? AND `product_id` = ? LIMIT 1")) {
            statement.setLong(1, memberId);
            statement.setLong(2, productId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void reassignProductIngredients(Connection connection, long keepId, long dupId) throws SQLException {
        List<Long> keepIngredientIds = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT `ingredient_id` FROM `product_ingredients` WHERE `product_id` = ?")) {
            select.setLong(1, keepId);
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    keepIngredientIds.add(result.getLong(1));
                }
            }
        }
        for (Long ingredientId : keepIngredientIds) {
            executeUpdate(connection,
                    "DELETE FROM `product_ingredients` WHERE `product_id` = ? AND `ingredient_id` = ?",
                    dupId, ingredientId);
        }
        executeUpdate(connection,
                "UPDATE `product_ingredients` SET `product_id` = ? WHERE `product_id` = ?",
                keepId, dupId);
    }

    private void enforceNotNullAndUnique(Connection connection) throws SQLException {
        execute(connection, "UPDATE `products` SET `normalized_name` = CONCAT('id', `id`) WHERE `normalized_name` IS NULL OR `normalized_name` = ''");
        if (isMySqlFamily(connection)) {
            execute(connection, "ALTER TABLE `products` MODIFY COLUMN `normalized_name` VARCHAR(255) NOT NULL");
        } else {
            execute(connection, "ALTER TABLE `products` ALTER COLUMN `normalized_name` VARCHAR(255) NOT NULL");
        }
        if (!indexExists(connection, PRODUCTS, "uk_products_normalized_name")) {
            execute(connection, "CREATE UNIQUE INDEX `uk_products_normalized_name` ON `products` (`normalized_name`)");
        }
    }

    private List<ProductRow> loadProducts(Connection connection) throws SQLException {
        List<ProductRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT `id`, `name` FROM `products`")) {
            while (result.next()) {
                rows.add(new ProductRow(result.getLong("id"), result.getString("name")));
            }
        }
        return rows;
    }

    private boolean isMySqlFamily(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        return productName.contains("mysql") || productName.contains("mariadb");
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getTables(connection.getCatalog(), null, table, new String[] {"TABLE"})) {
            if (result.next()) {
                return true;
            }
        }
        try (ResultSet result = metadata.getTables(connection.getCatalog(), null, table.toUpperCase(Locale.ROOT), new String[] {"TABLE"})) {
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

    private boolean indexExists(Connection connection, String table, String index) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (result.next()) {
                String name = result.getString("INDEX_NAME");
                if (index.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        try (ResultSet result = metadata.getIndexInfo(
                connection.getCatalog(), null, table.toUpperCase(Locale.ROOT), false, false)) {
            while (result.next()) {
                String name = result.getString("INDEX_NAME");
                if (index.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void executeUpdate(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
        }
    }

    private record ProductRow(long id, String name) {
    }
}
