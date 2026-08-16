package domain.routine.dto.response;

import domain.inventory.ProductCategory;
import domain.routine.domain.RoutineType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "AI 자동생성 루틴 미리보기 결과 (6.16). Routine 테이블에는 아무것도 저장하지 않는다.")
public record RoutineGenerationResponse(
        @Schema(description = "제안 루틴명") String suggestedName,
        @Schema(description = "루틴 타입 (DAY/NIGHT)") RoutineType routineType,
        @Schema(description = "제안 단계 목록") List<GeneratedStep> steps,
        @Schema(description = "선택된 조합 내부의 성분 충돌 경고 (없으면 빈 배열, 자동 교체는 하지 않음)") List<String> warnings
) {

    @Schema(description = "제안 단계 한 건")
    public record GeneratedStep(
            @Schema(description = "단계 순서") int order,
            @Schema(description = "인벤토리 ID") Long inventoryId,
            @Schema(description = "카테고리") ProductCategory category,
            @Schema(description = "제품명") String productName,
            @Schema(description = "피부적합도 매칭점수 (0~100)") int matchScore
    ) {
    }
}