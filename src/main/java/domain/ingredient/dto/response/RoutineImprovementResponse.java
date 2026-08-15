package domain.ingredient.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 루틴 개선 방향 제시 결과")
public record RoutineImprovementResponse(
        @Schema(description = "제품명") String productName,
        @Schema(description = "시너지 관계") Relation synergy,
        @Schema(description = "충돌 관계") Relation conflict,
        @Schema(description = "중복 관계") Duplicate duplicate
) {

    @Schema(description = "보유 제품과의 시너지/충돌 관계")
    public record Relation(
            @Schema(description = "관계 존재 여부") boolean exists,
            @Schema(description = "사용자에게 보여줄 안내 문구") String message,
            @Schema(description = "관계가 있는 보유 제품 (없으면 null)") MatchedProduct product
    ) {
    }

    @Schema(description = "보유 제품과의 중복 관계")
    public record Duplicate(
            @Schema(description = "중복 존재 여부") boolean exists,
            @Schema(description = "사용자에게 보여줄 안내 문구") String message,
            @Schema(description = "대신 사용할 수 있는 보유 제품 (없으면 null)") MatchedProduct alternativeProduct
    ) {
    }

    @Schema(description = "매칭된 보유 제품")
    public record MatchedProduct(
            @Schema(description = "상품 ID") Long productId,
            @Schema(description = "제품명") String productName
    ) {
    }
}
