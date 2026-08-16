package domain.routine.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "데일리 루틴 스텝 완료 토글 요청")
public record RoutineStepCompletionRequest(
        @Schema(description = "완료 여부") @NotNull Boolean completed
) {
}