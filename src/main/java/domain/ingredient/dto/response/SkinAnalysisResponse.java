package domain.ingredient.dto.response;

import domain.ingredient.domain.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "제품 피부적합도 통합 분석 결과")
public record SkinAnalysisResponse(
        @Schema(description = "제품 ID") Long productId,
        @Schema(description = "제품명") String productName,
        @Schema(description = "AI 종합 적합도 점수 (0~100). AI 분석 실패 시 EWG 등급 기반 폴백 점수") int matchScore,
        @Schema(description = "EWG 등급 기반 규칙으로 생성한 안전성 평가 한 줄 요약") String safetyEvaluation,
        @Schema(description = "AI 분석 근거") AiAnalysis aiAnalysis,
        @Schema(description = "전성분 프로필") IngredientProfile ingredientProfile,
        @Schema(description = "분석 일시") LocalDateTime analyzedAt
) {

    @Schema(description = "AI 분석 근거와 유의사항")
    public record AiAnalysis(
            @Schema(description = "점수 판단 근거 키워드 목록 (AI 분석 실패 시 빈 리스트)") List<AnalysisReason> reasons,
            @Schema(description = "유의사항 안내문") String disclaimer
    ) {
    }

    @Schema(description = "전성분 프로필")
    public record IngredientProfile(
            @Schema(description = "전체 성분 수") int totalCount,
            @Schema(description = "주의가 필요한 성분 수 (위험도 MID 이상)") int cautionCount,
            @Schema(description = "알레르기 유발 성분 수 (현재 데이터 미지원, 항상 0. TODO)") int allergyCount,
            @Schema(description = "위험도별 성분 분포") RiskDistribution riskDistribution,
            @Schema(description = "성분별 상세 정보") List<IngredientDetail> ingredients
    ) {
    }

    @Schema(description = "위험도별 성분 분포")
    public record RiskDistribution(
            @Schema(description = "낮음 등급 성분 수") int low,
            @Schema(description = "보통 등급 성분 수") int mid,
            @Schema(description = "위험 등급 성분 수") int high
    ) {
    }

    @Schema(description = "성분 상세 정보")
    public record IngredientDetail(
            @Schema(description = "전성분 표기 순위") int rank,
            @Schema(description = "성분명") String name,
            @Schema(description = "위험도") RiskLevel riskLevel,
            @Schema(description = "배합목적 태그 (현재 미지원, 항상 빈 리스트. TODO)") List<String> purposeTags,
            @Schema(description = "효능 태그 (현재 미지원, 항상 빈 리스트. TODO)") List<String> effectTags
    ) {
    }
}