package domain.ingredient.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "분석 근거 키워드와 상세 이유")
public record AnalysisReason(
        @Schema(description = "키워드") String keyword,
        @Schema(description = "상세 이유") String reason
) {
}