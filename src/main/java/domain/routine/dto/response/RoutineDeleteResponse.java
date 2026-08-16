package domain.routine.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "보관함 루틴 삭제 결과 (6.11)")
public record RoutineDeleteResponse(
        @Schema(description = "삭제된 루틴 ID") Long routineId
) {
}