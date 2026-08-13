package domain.cosmetic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "성분의 국가별 금지·제한 정보")
public class IngredientRegulationDto {

    @Schema(description = "해당 성분을 금지하는 국가 목록")
    private final List<String> prohibitedCountries;

    @Schema(description = "해당 성분을 제한(사용조건부 허용)하는 국가 목록")
    private final List<String> restrictedCountries;

    public IngredientRegulationDto(List<String> prohibitedCountries, List<String> restrictedCountries) {
        this.prohibitedCountries = prohibitedCountries == null ? List.of() : prohibitedCountries;
        this.restrictedCountries = restrictedCountries == null ? List.of() : restrictedCountries;
    }
}
