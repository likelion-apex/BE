package domain.inventory.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IngredientPurposeCategoryTest {

    @Test
    void fromRaw_mapsKnownEnumCodes() {
        assertThat(IngredientPurposeCategory.fromRaw("SOLVENT")).isEqualTo(IngredientPurposeCategory.SOLVENT);
        assertThat(IngredientPurposeCategory.fromRaw("fragrance"))
                .isEqualTo(IngredientPurposeCategory.FRAGRANCE);
    }

    @Test
    void fromRaw_mapsOfficialKoreanLabels() {
        assertThat(IngredientPurposeCategory.fromRaw("용제")).isEqualTo(IngredientPurposeCategory.SOLVENT);
        assertThat(IngredientPurposeCategory.fromRaw("피부컨디셔닝제"))
                .isEqualTo(IngredientPurposeCategory.SKIN_CONDITIONING_AGENT);
        assertThat(IngredientPurposeCategory.fromRaw("헤어컨디셔닝제"))
                .isEqualTo(IngredientPurposeCategory.HAIR_CONDITIONING_AGENT);
        assertThat(IngredientPurposeCategory.fromRaw("피부보호제"))
                .isEqualTo(IngredientPurposeCategory.SKIN_PROTECTANT);
        assertThat(IngredientPurposeCategory.fromRaw("착향제"))
                .isEqualTo(IngredientPurposeCategory.FRAGRANCE_ADDITIVE);
        assertThat(IngredientPurposeCategory.fromRaw("변성제")).isEqualTo(IngredientPurposeCategory.DENATURANT);
        assertThat(IngredientPurposeCategory.fromRaw("향료")).isEqualTo(IngredientPurposeCategory.FRAGRANCE);
    }

    @Test
    void fromRaw_looseMatchesLabelsWithExtraText() {
        assertThat(IngredientPurposeCategory.fromRaw("피부 보호제(자외선 차단)"))
                .isEqualTo(IngredientPurposeCategory.SKIN_PROTECTANT);
    }

    @Test
    void fromRaw_returnsNullWhenNullOrBlankOrUnmatched() {
        assertThat(IngredientPurposeCategory.fromRaw(null)).isNull();
        assertThat(IngredientPurposeCategory.fromRaw("")).isNull();
        assertThat(IngredientPurposeCategory.fromRaw("   ")).isNull();
        assertThat(IngredientPurposeCategory.fromRaw("알수없는목적")).isNull();
    }

    @Test
    void getLabel_returnsOfficialKoreanCategoryName() {
        assertThat(IngredientPurposeCategory.SOLVENT.getLabel()).isEqualTo("용제");
        assertThat(IngredientPurposeCategory.SKIN_CONDITIONING_AGENT.getLabel()).isEqualTo("피부컨디셔닝제");
        assertThat(IngredientPurposeCategory.HAIR_CONDITIONING_AGENT.getLabel()).isEqualTo("헤어컨디셔닝제");
        assertThat(IngredientPurposeCategory.SKIN_PROTECTANT.getLabel()).isEqualTo("피부보호제");
        assertThat(IngredientPurposeCategory.FRAGRANCE_ADDITIVE.getLabel()).isEqualTo("착향제");
        assertThat(IngredientPurposeCategory.DENATURANT.getLabel()).isEqualTo("변성제");
        assertThat(IngredientPurposeCategory.FRAGRANCE.getLabel()).isEqualTo("향료");
    }
}
