package domain.ingredient.dto.response;

import domain.ingredient.domain.AnalysisGrade;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 루틴 분석 결과")
public record AiRoutineAnalysisResponse(
        @Schema(description = "제품명") String productName,
        @Schema(description = "AI 종합 등급") AnalysisGrade grade,
        @Schema(description = "등급 판단 근거 코멘트") String comment
) {
}
