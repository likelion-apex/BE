package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 제품명 정규화 키를 추가하고, 같은 키를 가진 중복 상품을 하나로 합친다.
 *
 * <p>운영 MySQL은 루틴 스텝이 inventories/products를 참조하므로, 중복 인벤토리 행을 지우기 전에
 * 자식 FK를 먼저 옮긴다. 지우지 못한 행은 키 뒤에 id를 붙여 unique index가 실패하지 않게 한다.</p>
 */
public class V3__add_product_normalized_name extends BaseJavaMigration {

    private static final String PRODUCTS = "products";
    private static final String NORMALIZED_NAME = "normalized_name";
    private static final Set<String> PRODUCT_FK_HANDLED_TABLES = Set.of("inventories", "product_ingredients");

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!tableExists(connection, PRODUCTS)) {
            return;
        }
        addNormalizedNameColumn(connection);
        backfillAndMerge(connection);
        disambiguateRemainingDuplicates(connection);
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
            String key = canonicalKey(row.name);
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
        reassignRemainingProductForeignKeys(connection, keepId, dupId);
        try {
            executeUpdate(connection, "DELETE FROM `products` WHERE `id` = ?", dupId);
        } catch (SQLException exception) {
            System.err.println("V3: could not delete duplicate product " + dupId + ": " + exception.getMessage());
        }
    }

    private void reassignInventories(Connection connection, long keepId, long dupId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT `id`, `member_id` FROM `inventories` WHERE `product_id` = ?")) {
            select.setLong(1, dupId);
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    long inventoryId = result.getLong("id");
                    long memberId = result.getLong("member_id");
                    Long keepInventoryId = findInventoryId(connection, memberId, keepId);
                    if (keepInventoryId != null) {
                        retargetInventoryReferences(connection, inventoryId, keepInventoryId);
                        try {
                            executeUpdate(connection, "DELETE FROM `inventories` WHERE `id` = ?", inventoryId);
                        } catch (SQLException exception) {
                            System.err.println("V3: could not delete duplicate inventory "
                                    + inventoryId + ": " + exception.getMessage());
                        }
                    } else {
                        executeUpdate(connection,
                                "UPDATE `inventories` SET `product_id` = ? WHERE `id` = ?",
                                keepId, inventoryId);
                    }
                }
            }
        }
    }

    private Long findInventoryId(Connection connection, long memberId, long productId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT `id` FROM `inventories` WHERE `member_id` = ? AND `product_id` = ? LIMIT 1")) {
            statement.setLong(1, memberId);
            statement.setLong(2, productId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getLong(1);
                }
                return null;
            }
        }
    }

    private void retargetInventoryReferences(Connection connection, long fromInventoryId, long toInventoryId)
            throws SQLException {
        if (tableExists(connection, "routine_steps") && columnExists(connection, "routine_steps", "inventory_id")) {
            executeUpdate(connection,
                    "UPDATE `routine_steps` SET `inventory_id` = ? WHERE `inventory_id` = ?",
                    toInventoryId, fromInventoryId);
        }
        for (FkColumn fk : exportedKeys(connection, "inventories")) {
            if ("routine_steps".equalsIgnoreCase(fk.table) && "inventory_id".equalsIgnoreCase(fk.column)) {
                continue;
            }
            executeUpdate(connection,
                    "UPDATE `" + fk.table + "` SET `" + fk.column + "` = ? WHERE `" + fk.column + "` = ?",
                    toInventoryId, fromInventoryId);
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

    private void reassignRemainingProductForeignKeys(Connection connection, long keepId, long dupId)
            throws SQLException {
        if (tableExists(connection, "routine_steps") && columnExists(connection, "routine_steps", "product_id")) {
            executeUpdate(connection,
                    "UPDATE `routine_steps` SET `product_id` = ? WHERE `product_id` = ?",
                    keepId, dupId);
        }
        for (FkColumn fk : exportedKeys(connection, PRODUCTS)) {
            if (PRODUCT_FK_HANDLED_TABLES.contains(fk.table.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if ("routine_steps".equalsIgnoreCase(fk.table) && "product_id".equalsIgnoreCase(fk.column)) {
                continue;
            }
            try {
                executeUpdate(connection,
                        "UPDATE `" + fk.table + "` SET `" + fk.column + "` = ? WHERE `" + fk.column + "` = ?",
                        keepId, dupId);
            } catch (SQLException exception) {
                System.err.println("V3: could not retarget " + fk.table + "." + fk.column
                        + " from product " + dupId + ": " + exception.getMessage());
            }
        }
    }

    private void disambiguateRemainingDuplicates(Connection connection) throws SQLException {
        List<ProductRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT `id`, `normalized_name` FROM `products`")) {
            while (result.next()) {
                rows.add(new ProductRow(result.getLong(1), result.getString(2)));
            }
        }
        Map<String, List<Long>> idsByKey = new LinkedHashMap<>();
        for (ProductRow row : rows) {
            String key = row.name;
            if (key == null || key.isBlank()) {
                key = "id" + row.id;
                executeUpdate(connection,
                        "UPDATE `products` SET `normalized_name` = ? WHERE `id` = ?",
                        key, row.id);
            }
            idsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row.id);
        }
        for (Map.Entry<String, List<Long>> entry : idsByKey.entrySet()) {
            List<Long> ids = entry.getValue();
            if (ids.size() < 2) {
                continue;
            }
            Long keepId = ids.stream().min(Long::compareTo).orElseThrow();
            for (Long id : ids) {
                if (id.equals(keepId)) {
                    continue;
                }
                executeUpdate(connection,
                        "UPDATE `products` SET `normalized_name` = ? WHERE `id` = ?",
                        entry.getKey() + "#" + id, id);
            }
        }
    }

    private void enforceNotNullAndUnique(Connection connection) throws SQLException {
        execute(connection,
                "UPDATE `products` SET `normalized_name` = CONCAT('id', `id`) WHERE `normalized_name` IS NULL OR `normalized_name` = ''");
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

    static String canonicalKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String nfc = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC);
        StringBuilder hangul = new StringBuilder();
        StringBuilder alnum = new StringBuilder();
        for (int i = 0; i < nfc.length(); ) {
            int codePoint = nfc.codePointAt(i);
            if (codePoint >= 0xAC00 && codePoint <= 0xD7A3) {
                hangul.appendCodePoint(codePoint);
            } else if (Character.isLetterOrDigit(codePoint)) {
                alnum.appendCodePoint(Character.toLowerCase(codePoint));
            }
            i += Character.charCount(codePoint);
        }
        if (!hangul.isEmpty()) {
            return hangul.toString();
        }
        if (!alnum.isEmpty()) {
            return alnum.toString();
        }
        return nfc.replaceAll("\\s+", "");
    }

    private List<FkColumn> exportedKeys(Connection connection, String pkTable) throws SQLException {
        Set<FkColumn> keys = new LinkedHashSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        for (String tableName : List.of(pkTable, pkTable.toLowerCase(Locale.ROOT), pkTable.toUpperCase(Locale.ROOT))) {
            collectExportedKeys(metadata, catalog, tableName, keys);
            if (catalog != null) {
                collectExportedKeys(metadata, null, tableName, keys);
            }
        }
        return List.copyOf(keys);
    }

    private void collectExportedKeys(
            DatabaseMetaData metadata, String catalog, String tableName, Set<FkColumn> keys) throws SQLException {
        try (ResultSet result = metadata.getExportedKeys(catalog, null, tableName)) {
            while (result.next()) {
                String table = result.getString("FKTABLE_NAME");
                String column = result.getString("FKCOLUMN_NAME");
                if (table != null && column != null && table.matches("[A-Za-z0-9_]+") && column.matches("[A-Za-z0-9_]+")) {
                    keys.add(new FkColumn(table, column));
                }
            }
        }
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

    private record FkColumn(String table, String column) {
    }
}
