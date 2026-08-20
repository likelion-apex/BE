package domain.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CautionIngredientCatalogTest {

    private final CautionIngredientCatalog catalog = new CautionIngredientCatalog();

    @Test
    void countCaution20_matchesExactAndSuffixForms() {
        int count = catalog.countCaution20(List.of("파라벤", "메틸파라벤", "정제수"));

        assertThat(count).isEqualTo(2);
    }

    @Test
    void countCaution20_matchesAliasEntry() {
        int count = catalog.countCaution20(List.of("벤조페논-3"));

        assertThat(count).isEqualTo(1);
    }

    @Test
    void countCaution20_returnsZeroWhenNoMatch() {
        int count = catalog.countCaution20(List.of("정제수", "글리세린"));

        assertThat(count).isZero();
    }

    @Test
    void countCaution20_returnsZeroForEmptyOrNullInput() {
        assertThat(catalog.countCaution20(List.of())).isZero();
        assertThat(catalog.countCaution20(null)).isZero();
    }

    @Test
    void countAllergens_matchesExactAndIgnoresWhitespace() {
        int count = catalog.countAllergens(List.of("리모넨", "제라니올", "정제수"));

        assertThat(count).isEqualTo(2);
    }

    @Test
    void countAllergens_returnsZeroWhenNoMatch() {
        int count = catalog.countAllergens(List.of("정제수", "글리세린"));

        assertThat(count).isZero();
    }
}
