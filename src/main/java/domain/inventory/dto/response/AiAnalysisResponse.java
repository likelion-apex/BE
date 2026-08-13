package domain.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "AI 맞춤 분석 결과")
public record AiAnalysisResponse(
        @Schema(description = "인벤토리 ID") Long inventoryId,
        @Schema(description = "제품명") String productName,
        @Schema(description = "AI 종합 점수 (0~100)") int score,
        @Schema(description = "점수 판단 근거가 되는 키워드 목록") List<AnalysisKeyword> keywords,
        @Schema(description = "분석 일시") LocalDateTime analyzedAt
) {

    @Schema(description = "분석 키워드와 상세 이유")
    public record AnalysisKeyword(
            @Schema(description = "키워드") String keyword,
            @Schema(description = "상세 이유") String reason
    ) {
    }
}
