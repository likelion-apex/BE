package domain.routine.dto.response;

import domain.routine.domain.Routine;
import domain.routine.domain.RoutineStatus;
import domain.routine.domain.RoutineStep;
import domain.routine.domain.RoutineType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;

@Schema(description = "보관함 루틴 상세 조회 결과 (6.10)")
public record RoutineDetailResponse(
        @Schema(description = "루틴 ID") Long routineId,
        @Schema(description = "루틴명") String name,
        @Schema(description = "루틴 타입 (DAY/NIGHT)") RoutineType routineType,
        @Schema(description = "루틴 상태") RoutineStatus status,
        @Schema(description = "루틴 단계 목록 (순서대로)") List<RoutineDetailStepResponse> steps
) {

    public static RoutineDetailResponse from(Routine routine) {
        List<RoutineDetailStepResponse> steps = routine.getSteps().stream()
                .sorted(Comparator.comparingInt(RoutineStep::getOrder))
                .map(RoutineDetailStepResponse::from)
                .toList();
        return new RoutineDetailResponse(routine.getId(), routine.getName(), routine.getRoutineType(), routine.getStatus(), steps);
    }

    @Schema(description = "루틴 단계 상세 한 건")
    public record RoutineDetailStepResponse(
            @Schema(description = "단계 순서") int order,
            @Schema(description = "제품 ID (마스터 카탈로그, 없으면 null)") Long productId,
            @Schema(description = "인벤토리 ID (내 보유 제품, 없으면 null)") Long inventoryId,
            @Schema(description = "제품명") String productName,
            @Schema(description = "브랜드") String brand,
            @Schema(description = "카테고리") String category,
            @Schema(description = "이미지 URL") String imageUrl,
            @Schema(description = "AI 추천 사유 (없으면 null)") String aiReason
    ) {

        static RoutineDetailStepResponse from(RoutineStep step) {
            return new RoutineDetailStepResponse(
                    step.getOrder(),
                    step.getProduct() != null ? step.getProduct().getId() : null,
                    step.getInventory() != null ? step.getInventory().getId() : null,
                    step.getProductName(),
                    step.getBrand(),
                    step.getCategory(),
                    step.getImageUrl(),
                    step.getAiReason()
            );
        }
    }
}