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

class V8__migrate_product_categories_v2Test {

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:product_category_migration_v2;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
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
                    (1, '코스알엑스 더 나이아신아마이드 15 세럼', 'SERUM'),
                    (2, '설화수 윤조에센스', 'ESSENCE'),
                    (3, '라운드랩 자작나무 수분 선크림', 'SUNCREAM'),
                    (4, '비플레인 녹두 약산성 클렌징폼', 'CLEANSER'),
                    (5, '라네즈 워터 슬리핑 마스크', 'MASK'),
                    (6, '라운드랩 1025 독도 토너', 'SKIN_TONER'),
                    (7, '일리윤 세라마이드 아토 로션', 'LOTION'),
                    (8, '닥터지 레드 블레미쉬 클리어 수딩 크림', 'CREAM'),
                    (9, '아벤느 오 떼르말 미스트', 'ETC')
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
        V8__migrate_product_categories_v2 migration = new V8__migrate_product_categories_v2();

        migration.migrate(context);
        migration.migrate(context);

        assertThat(categories()).containsExactly(
                entry(1L, "ESSENCE_SERUM"),
                entry(2L, "ESSENCE_SERUM"),
                entry(3L, null),
                entry(4L, null),
                entry(5L, null),
                entry(6L, "SKIN_TONER"),
                entry(7L, "LOTION"),
                entry(8L, "CREAM"),
                entry(9L, null)
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
