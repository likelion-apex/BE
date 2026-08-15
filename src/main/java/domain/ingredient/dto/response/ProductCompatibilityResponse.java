package domain.ingredient.dto.response;

import domain.ingredient.domain.InteractionType;
import domain.inventory.ProductCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "제품 궁합 비교 결과")
public record ProductCompatibilityResponse(
        @Schema(description = "기준 제품 ID") Long baseProductId,
        @Schema(description = "기준 제품명") String baseProductName,
        @Schema(description = "기준 제품 카테고리") ProductCategory baseCategory,
        @Schema(description = "비교 결과 목록 (존재하지 않는 compareProductId는 제외됨)") List<CompatibilityResult> results
) {

    @Schema(description = "기준 제품과 비교 제품 1개의 궁합 결과")
    public record CompatibilityResult(
            @Schema(description = "비교 제품 ID") Long compareProductId,
            @Schema(description = "비교 제품명") String compareProductName,
            @Schema(description = "비교 제품 카테고리") ProductCategory category,
            @Schema(description = "궁합 판정") InteractionType interactionType,
            @Schema(description = "판정 사유 설명") String description
    ) {
    }
}
