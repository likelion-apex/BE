package domain.routine.dto.response;

import domain.routine.domain.RoutineStatus;
import domain.routine.domain.RoutineType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "루틴 생성 결과 (6.17)")
public record RoutineCreateResponse(
        @Schema(description = "생성된 루틴 ID") Long routineId,
        @Schema(description = "루틴명") String name,
        @Schema(description = "루틴 타입 (DAY/NIGHT)") RoutineType routineType,
        @Schema(description = "루틴 상태") RoutineStatus status
) {
}