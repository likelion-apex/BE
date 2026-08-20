package domain.routine.dto.response;

import domain.routine.domain.Routine;
import domain.routine.domain.RoutineStatus;
import domain.routine.domain.RoutineStep;
import domain.routine.domain.RoutineType;
import global.util.PublicUrlResolver;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Schema(description = "보관함 루틴 상세 조회 결과 (6.10)")
public record RoutineDetailResponse(
        @Schema(description = "루틴 ID") Long routineId,
        @Schema(description = "루틴명") String name,
        @Schema(description = "루틴 타입 (DAY/NIGHT)") RoutineType routineType,
        @Schema(description = "루틴 상태") RoutineStatus status,
        @Schema(description = "루틴 단계 목록 (순서대로)") List<RoutineDetailStepResponse> steps,
        @Schema(description = "AI 브리핑. 숏폼 분석 기반 루틴이 아니면(수동생성/AI자동생성) null")
        AiBriefing aiBriefing
) {

    public static RoutineDetailResponse from(Routine routine, PublicUrlResolver publicUrlResolver) {
        List<RoutineDetailStepResponse> steps = routine.getSteps().stream()
                .sorted(Comparator.comparingInt(RoutineStep::getOrder))
                .map(step -> RoutineDetailStepResponse.from(step, publicUrlResolver))
    public static RoutineDetailResponse from(
            Routine routine, AiBriefing aiBriefing, Map<Integer, String> safetyEvaluationByOrder) {
        List<RoutineDetailStepResponse> steps = routine.getSteps().stream()
                .sorted(Comparator.comparingInt(RoutineStep::getOrder))
                .map(step -> RoutineDetailStepResponse.from(step, safetyEvaluationByOrder.get(step.getOrder())))
                .toList();
        return new RoutineDetailResponse(
                routine.getId(), routine.getName(), routine.getRoutineType(), routine.getStatus(),
                steps, aiBriefing);
    }

    @Schema(description = "AI 브리핑 (숏폼 분석 원본 + 인벤토리 최적화 결과 합성)")
    public record AiBriefing(
            @Schema(description = "루틴 제목 (원본 영상 분석 기준, 파싱 실패/미완성 시 null)") String title,
            @Schema(description = "루틴 태그 (원본 영상 분석 기준, 파싱 실패/미완성 시 null)") String tag,
            @Schema(description = "루틴 핵심 목표 (원본 영상 분석 기준, 파싱 실패/미완성 시 null)") String coreGoal,
            @Schema(description = "시너지 성분 조합 (원본 영상 분석 기준, 파싱 실패/미완성 시 null)") String synergyCombo,
            @Schema(description = "인벤토리 대체를 반영한 최종 매칭 점수 (미최적화/파싱 실패 시 null)") Integer matchScore,
            @Schema(description = "인벤토리 대체를 반영한 최종 요약 (미최적화/파싱 실패 시 null)") String summary
    ) {
    }

    @Schema(description = "보관함 루틴 단계 상세 한 건")
    public record RoutineDetailStepResponse(
            @Schema(description = "단계 순서") int order,
            @Schema(description = "제품 ID (마스터 카탈로그, 없으면 null)") Long productId,
            @Schema(description = "인벤토리 ID (내 보유 제품, 없으면 null)") Long inventoryId,
            @Schema(description = "제품명") String productName,
            @Schema(description = "브랜드") String brand,
            @Schema(description = "카테고리") String category,
            @Schema(description = "이미지 URL") String imageUrl,
            @Schema(description = "AI 추천 사유 (없으면 null)") String aiReason,
            @Schema(description = "AI 안전성 평가 (4.4 피부적합도 분석의 첫 번째 근거, 제품 없거나 분석 실패 시 null)")
            String safetyEvaluation
    ) {

        static RoutineDetailStepResponse from(RoutineStep step, PublicUrlResolver publicUrlResolver) {
        static RoutineDetailStepResponse from(RoutineStep step, String safetyEvaluation) {
            return new RoutineDetailStepResponse(
                    step.getOrder(),
                    step.getProduct() != null ? step.getProduct().getId() : null,
                    step.getInventory() != null ? step.getInventory().getId() : null,
                    step.getProductName(),
                    step.getBrand(),
                    step.getCategory(),
                    publicUrlResolver.resolve(step.getImageUrl()),
                    step.getAiReason()
                    step.getImageUrl(),
                    step.getAiReason(),
                    safetyEvaluation
            );
        }
    }
}