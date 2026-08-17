package db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class V3__add_product_normalized_nameTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:product_normalized_name;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        );
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("""
                    CREATE TABLE products (
                        id BIGINT PRIMARY KEY,
                        name VARCHAR(255) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE inventories (
                        id BIGINT PRIMARY KEY,
                        member_id BIGINT NOT NULL,
                        product_id BIGINT NOT NULL,
                        FOREIGN KEY (product_id) REFERENCES products(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE routine_steps (
                        id BIGINT PRIMARY KEY,
                        product_id BIGINT,
                        inventory_id BIGINT,
                        FOREIGN KEY (product_id) REFERENCES products(id),
                        FOREIGN KEY (inventory_id) REFERENCES inventories(id)
                    )
                    """);
            statement.execute("""
                    INSERT INTO products (id, name) VALUES
                    (1, '바닥 토너'),
                    (2, '바닥 토너 01'),
                    (3, '다른 크림')
                    """);
            statement.execute("""
                    INSERT INTO inventories (id, member_id, product_id) VALUES
                    (10, 1, 1),
                    (11, 1, 2),
                    (12, 2, 2)
                    """);
            statement.execute("""
                    INSERT INTO routine_steps (id, product_id, inventory_id) VALUES
                    (20, 2, 11)
                    """);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void mergesDuplicateHangulKeysAndIsIdempotent() throws Exception {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        V3__add_product_normalized_name migration = new V3__add_product_normalized_name();

        migration.migrate(context);
        migration.migrate(context);

        assertThat(productName(1)).isEqualTo("바닥 토너");
        assertThat(normalizedName(1)).isEqualTo("바닥토너");
        assertThat(productExists(2)).isFalse();
        assertThat(normalizedName(3)).isEqualTo("다른크림");
        assertThat(inventoryProduct(10)).isEqualTo(1L);
        assertThat(inventoryExists(11)).isFalse();
        assertThat(inventoryProduct(12)).isEqualTo(1L);
        assertThat(stepProduct(20)).isEqualTo(1L);
        assertThat(stepInventory(20)).isEqualTo(10L);
    }

    private String productName(long id) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT name FROM products WHERE id = " + id)) {
            result.next();
            return result.getString(1);
        }
    }

    private String normalizedName(long id) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT normalized_name FROM products WHERE id = " + id)) {
            result.next();
            return result.getString(1);
        }
    }

    private boolean productExists(long id) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT 1 FROM products WHERE id = " + id)) {
            return result.next();
        }
    }

    private long inventoryProduct(long id) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT product_id FROM inventories WHERE id = " + id)) {
            result.next();
            return result.getLong(1);
        }
    }

    private boolean inventoryExists(long id) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT 1 FROM inventories WHERE id = " + id)) {
            return result.next();
        }
    }

    private long stepProduct(long id) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT product_id FROM routine_steps WHERE id = " + id)) {
            result.next();
            return result.getLong(1);
        }
    }

    private long stepInventory(long id) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT inventory_id FROM routine_steps WHERE id = " + id)) {
            result.next();
            return result.getLong(1);
        }
    }
}
