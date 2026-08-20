package domain.routine.dto.response;

import domain.routine.domain.Routine;
import domain.routine.domain.RoutineLog;
import domain.routine.domain.RoutineLogStep;
import domain.routine.domain.RoutineStep;
import domain.routine.domain.RoutineType;
import domain.routine.dto.response.RoutineDetailResponse.AiBriefing;
import global.util.PublicUrlResolver;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Schema(description = "오늘의 데일리 루틴 조회 결과 (활성 루틴 없으면 null)")
public record DailyRoutineResponse(
        @Schema(description = "루틴 ID") Long routineId,
        @Schema(description = "루틴명") String name,
        @Schema(description = "루틴 타입 (DAY/NIGHT)") RoutineType routineType,
        @Schema(description = "오늘 전체완료 처리 여부") boolean completed,
        @Schema(description = "달성률 (0~100)") int completionRate,
        @Schema(description = "루틴 단계 목록 (순서대로)") List<DailyRoutineStepResponse> steps,
        @Schema(description = "AI 브리핑. 숏폼 분석 기반 루틴이 아니면(수동생성/AI자동생성) null")
        AiBriefing aiBriefing
) {

    public static DailyRoutineResponse from(
            Routine routine, RoutineLog routineLog, AiBriefing aiBriefing, PublicUrlResolver publicUrlResolver) {
        Map<Long, RoutineStep> stepById = routine.getSteps().stream()
                .collect(Collectors.toMap(RoutineStep::getId, Function.identity()));
        boolean hasSourceAnalysis = routine.getSourceAnalysis() != null;

        List<DailyRoutineStepResponse> steps = routineLog.getSteps().stream()
                .sorted(Comparator.comparingInt(RoutineLogStep::getOrder))
                .map(logStep -> DailyRoutineStepResponse.from(
                        logStep, stepById.get(logStep.getRoutineStepId()), publicUrlResolver, hasSourceAnalysis))
                .toList();

        long completedCount = steps.stream().filter(DailyRoutineStepResponse::completed).count();
        int completionRate = steps.isEmpty() ? 0 : (int) Math.round(completedCount * 100.0 / steps.size());

        return new DailyRoutineResponse(
                routine.getId(), routine.getName(), routine.getRoutineType(),
                routineLog.isCompleted(), completionRate, steps, aiBriefing);
    }

    @Schema(description = "데일리 루틴 단계 한 건")
    public record DailyRoutineStepResponse(
            @Schema(description = "루틴 로그 스텝 ID (6.2/6.13 API의 stepId). "
                    + "과거 날짜 조회 시에는 참고용입니다 (6.2 토글은 오늘 날짜에서만 유효)") Long stepId,
            @Schema(description = "단계 순서") int order,
            @Schema(description = "제품 ID (마스터 카탈로그, 없으면 null)") Long productId,
            @Schema(description = "인벤토리 ID (내 보유 제품, 없으면 null)") Long inventoryId,
            @Schema(description = "제품명") String productName,
            @Schema(description = "브랜드") String brand,
            @Schema(description = "카테고리") String category,
            @Schema(description = "이미지 URL") String imageUrl,
            @Schema(description = "완료 여부") boolean completed,
            @Schema(description = "대체 여부. 인벤토리 제품으로 대체됐으면 REPLACED, 영상 속 제품 그대로면 VIDEO_PRODUCT")
            String status
    ) {

        public static DailyRoutineStepResponse from(
                RoutineLogStep logStep, RoutineStep step, PublicUrlResolver publicUrlResolver,
                boolean hasSourceAnalysis) {
            return new DailyRoutineStepResponse(
                    logStep.getId(),
                    logStep.getOrder(),
                    step != null && step.getProduct() != null ? step.getProduct().getId() : null,
                    step != null && step.getInventory() != null ? step.getInventory().getId() : null,
                    step != null ? step.getProductName() : null,
                    step != null ? step.getBrand() : null,
                    step != null ? step.getCategory() : null,
                    step != null ? publicUrlResolver.resolve(step.getImageUrl()) : null,
                    logStep.isCompleted(),
                    step != null && hasSourceAnalysis
                            ? (step.getInventory() != null ? "REPLACED" : "VIDEO_PRODUCT")
                            : null
            );
        }
    }
}