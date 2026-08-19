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

class V10__reset_product_image_urls_to_categoryTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:reset_product_image_urls;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        );
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("""
                    CREATE TABLE products (
                        id BIGINT PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        category VARCHAR(32),
                        image_url VARCHAR(500)
                    )
                    """);
            statement.execute("""
                    INSERT INTO products (id, name, category, image_url) VALUES
                    (1, '라운드랩 1025 독도 토너', 'SKIN_TONER',
                        'https://search1.kakaocdn.net/argon/130x130_85_c/legacy?query=랑콤'),
                    (2, '닥터지 레드 블레미쉬 클리어 수딩 크림', 'CREAM', '/images/categories/cream.png'),
                    (3, '보유 기타 제품', NULL, 'https://search1.kakaocdn.net/argon/etc.jpg')
                    """);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void resetsEveryImageUrlToCategoryPathAndIsIdempotent() throws Exception {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        V10__reset_product_image_urls_to_category migration =
                new V10__reset_product_image_urls_to_category();

        migration.migrate(context);
        migration.migrate(context);

        assertThat(imageUrls()).containsExactly(
                entry(1L, "/images/categories/skin_toner.png"),
                entry(2L, "/images/categories/cream.png"),
                entry(3L, null)
        );
    }

    private Map<Long, String> imageUrls() throws SQLException {
        Map<Long, String> imageUrls = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id, image_url FROM products ORDER BY id")) {
            while (result.next()) {
                imageUrls.put(result.getLong("id"), result.getString("image_url"));
            }
        }
        return imageUrls;
    }
}
