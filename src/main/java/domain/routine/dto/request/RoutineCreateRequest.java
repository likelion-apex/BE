package domain.routine.dto.request;

import domain.beauty.shortform.domain.RoutineSaveType;
import domain.routine.domain.RoutineType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.util.List;

@Schema(description = "루틴 생성 요청 (6.17)")
public record RoutineCreateRequest(
        @Schema(description = "루틴명") @NotNull String name,
        @Schema(description = "루틴 타입 (DAY/NIGHT)") @NotNull RoutineType routineType,
        @Schema(description = "저장 방식 (TODAY/LIBRARY)") @NotNull RoutineSaveType saveType,
        @Schema(description = "루틴 단계 목록") @NotEmpty @Valid List<RoutineStepCreateRequest> steps
) {

    @Schema(description = "루틴 생성 시 단계 한 건")
    public record RoutineStepCreateRequest(
            @Schema(description = "단계 순서") int order,
            @Schema(description = "인벤토리 ID (본인 소유여야 함)") @NotNull Long inventoryId
    ) {
    }
}