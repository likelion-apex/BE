package domain.home.dto.response;

import domain.routine.domain.Routine;
import domain.routine.domain.RoutineStep;
import domain.routine.domain.RoutineType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;

@Schema(description = "오늘의 활성 루틴 (없으면 null)")
public record TodayRoutineResponse(
        @Schema(description = "루틴 ID") Long routineId,
        @Schema(description = "루틴명") String name,
        @Schema(description = "루틴 타입 (DAY/NIGHT)") RoutineType routineType,
        @Schema(description = "루틴 단계 목록 (순서대로)") List<RoutineStepSummary> steps
) {

    public static TodayRoutineResponse from(Routine routine) {
        List<RoutineStepSummary> steps = routine.getSteps().stream()
                .sorted(Comparator.comparingInt(RoutineStep::getOrder))
                .map(RoutineStepSummary::from)
                .toList();
        return new TodayRoutineResponse(routine.getId(), routine.getName(), routine.getRoutineType(), steps);
    }

    @Schema(description = "루틴 단계 한 건 (완료여부/달성률 없음)")
    public record RoutineStepSummary(
            @Schema(description = "단계 순서") int order,
            @Schema(description = "제품 ID (마스터 카탈로그, 없으면 null)") Long productId,
            @Schema(description = "인벤토리 ID (내 보유 제품, 없으면 null)") Long inventoryId,
            @Schema(description = "제품명") String productName,
            @Schema(description = "브랜드") String brand,
            @Schema(description = "카테고리") String category,
            @Schema(description = "이미지 URL") String imageUrl
    ) {

        public static RoutineStepSummary from(RoutineStep step) {
            return new RoutineStepSummary(
                    step.getOrder(),
                    step.getProduct() != null ? step.getProduct().getId() : null,
                    step.getInventory() != null ? step.getInventory().getId() : null,
                    step.getProductName(),
                    step.getBrand(),
                    step.getCategory(),
                    step.getImageUrl()
            );
        }
    }
}
