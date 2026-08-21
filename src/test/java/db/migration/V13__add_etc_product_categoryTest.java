package db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class V13__add_etc_product_categoryTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:add_etc_product_category;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        );
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("""
                    CREATE TABLE products (
                        id BIGINT PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        category VARCHAR(32)
                    )
                    """);
            statement.execute("""
                    INSERT INTO products (id, name, category) VALUES
                    (1, '라운드랩 1025 독도 토너', 'SKIN_TONER'),
                    (2, '일리윤 세라마이드 아토 로션', 'LOTION'),
                    (3, '닥터지 레드 블레미쉬 클리어 수딩 크림', 'CREAM'),
                    (4, '분류되지 않은 선크림', NULL)
                    """);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void leavesExistingRowsUnchangedAndIsIdempotent() throws Exception {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        V13__add_etc_product_category migration = new V13__add_etc_product_category();

        migration.migrate(context);
        migration.migrate(context);

        assertThat(categories()).containsExactly(
                entry(1L, "SKIN_TONER"),
                entry(2L, "LOTION"),
                entry(3L, "CREAM"),
                entry(4L, null)
        );
    }

    @Test
    void allowsEtcValueAfterMigration() throws Exception {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        new V13__add_etc_product_category().migrate(context);

        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO products (id, name, category) VALUES
                    (5, '라운드랩 자작나무 수분 선크림', 'ETC')
                    """);
        }

        assertThat(categories()).contains(entry(5L, "ETC"));
    }

    private Map<Long, String> categories() throws SQLException {
        Map<Long, String> categories = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id, category FROM products ORDER BY id")) {
            while (result.next()) {
                categories.put(result.getLong("id"), result.getString("category"));
            }
        }
        return categories;
    }
}
