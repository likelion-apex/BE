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

class V2__migrate_product_categoriesTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:product_category_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
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
                    (1, '달바 화이트 트러플 퍼스트 스프레이 세럼', 'ESSENCE_AMPOULE_SERUM'),
                    (2, '진정 에센스', 'ESSENCE_AMPOULE_SERUM'),
                    (3, '보습 에멀전', 'LOTION_EMULSION'),
                    (4, '토너 패드', 'SKIN_TONER_PAD'),
                    (5, '페이스 오일', 'FACE_OIL'),
                    (6, '수분 크림', 'CREAM')
                    """);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void convertsLegacyValuesAndIsIdempotent() throws Exception {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        V2__migrate_product_categories migration = new V2__migrate_product_categories();

        migration.migrate(context);
        migration.migrate(context);

        assertThat(categories()).containsExactly(
                entry(1L, "SERUM"),
                entry(2L, "ESSENCE"),
                entry(3L, "LOTION"),
                entry(4L, "SKIN_TONER"),
                entry(5L, "ETC"),
                entry(6L, "CREAM")
        );
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
