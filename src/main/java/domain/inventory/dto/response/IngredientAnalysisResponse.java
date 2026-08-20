package domain.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "성분 분석 결과")
public record IngredientAnalysisResponse(
        @Schema(description = "인벤토리 ID") Long inventoryId,
        @Schema(description = "제품명") String productName,
        @Schema(description = "브랜드 (없으면 null)") String brand,
        @Schema(description = "용량 (제품명에 포함된 경우 파싱, 없으면 null)") String capacity,
        @Schema(description = "전성분 목록과 배합목적/위험도") List<IngredientDetail> ingredients,
        @Schema(description = "성분 위험도별 개수") RiskDistribution riskDistribution,
        @Schema(description = "20가지 주의성분 리스트에 매칭된 성분 개수") int caution20Count,
        @Schema(description = "알레르기 유발 의심성분 리스트에 매칭된 성분 개수") int allergyCount
) {

    @Schema(description = "성분명과 배합목적, 효능, 위험도")
    public record IngredientDetail(
            @Schema(description = "성분명") String ingredientName,
            @Schema(description = "배합목적 목록 (7개 공식 카테고리 중 최소 1개)") List<IngredientPurposeCategory> purposes,
            @Schema(description = "피부 효능 태그 (피부 보습/피부 보호, 0~2개)") List<SkinEfficacyTag> efficacyTags,
            @Schema(description = "위험도 (낮음/중간/높음)") IngredientRiskLevel riskLevel
    ) {
    }

    @Schema(description = "위험도별 성분 개수")
    public record RiskDistribution(
            @Schema(description = "낮음 등급 성분 수") int low,
            @Schema(description = "중간 등급 성분 수") int medium,
            @Schema(description = "높음 등급 성분 수") int high
    ) {
    }
}
