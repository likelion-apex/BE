package domain.cosmetic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class IngredientDetailDto {

    @Schema(description = "성분명", example = "글리세린")
    private final String name;

    @Schema(description = "성분 영문명", example = "Glycerin")
    private final String englishName;

    @Schema(description = "성분 규제정보 (금지/제한국가)")
    private final IngredientRegulationDto regulation;

    public IngredientDetailDto(String name, String englishName, IngredientRegulationDto regulation) {
        this.name = name;
        this.englishName = englishName;
        this.regulation = regulation;
    }
}
