package domain.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "성분 분석 결과")
public record IngredientAnalysisResponse(
        @Schema(description = "인벤토리 ID") Long inventoryId,
        @Schema(description = "제품명") String productName,
        @Schema(description = "전성분 목록과 배합목적") List<IngredientPurpose> ingredients
) {

    @Schema(description = "성분명과 배합목적 목록")
    public record IngredientPurpose(
            @Schema(description = "성분명") String ingredientName,
            @Schema(description = "배합목적 목록") List<String> purposes
    ) {
    }
}
