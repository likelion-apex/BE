package domain.inventory.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IngredientRiskLevelTest {

    @Test
    void fromRaw_mapsKnownEnumCodes() {
        assertThat(IngredientRiskLevel.fromRaw("LOW")).isEqualTo(IngredientRiskLevel.LOW);
        assertThat(IngredientRiskLevel.fromRaw("medium")).isEqualTo(IngredientRiskLevel.MEDIUM);
        assertThat(IngredientRiskLevel.fromRaw("High")).isEqualTo(IngredientRiskLevel.HIGH);
    }

    @Test
    void fromRaw_mapsKoreanLabels() {
        assertThat(IngredientRiskLevel.fromRaw("낮음")).isEqualTo(IngredientRiskLevel.LOW);
        assertThat(IngredientRiskLevel.fromRaw("중간")).isEqualTo(IngredientRiskLevel.MEDIUM);
        assertThat(IngredientRiskLevel.fromRaw("높음")).isEqualTo(IngredientRiskLevel.HIGH);
    }

    @Test
    void fromRaw_defaultsToMediumWhenNullOrBlankOrUnknown() {
        assertThat(IngredientRiskLevel.fromRaw(null)).isEqualTo(IngredientRiskLevel.MEDIUM);
        assertThat(IngredientRiskLevel.fromRaw("")).isEqualTo(IngredientRiskLevel.MEDIUM);
        assertThat(IngredientRiskLevel.fromRaw("   ")).isEqualTo(IngredientRiskLevel.MEDIUM);
        assertThat(IngredientRiskLevel.fromRaw("모르겠음")).isEqualTo(IngredientRiskLevel.MEDIUM);
    }

    @Test
    void getLabel_returnsKoreanDisplayText() {
        assertThat(IngredientRiskLevel.LOW.getLabel()).isEqualTo("낮음");
        assertThat(IngredientRiskLevel.MEDIUM.getLabel()).isEqualTo("중간");
        assertThat(IngredientRiskLevel.HIGH.getLabel()).isEqualTo("높음");
    }
}
