package domain.beauty.shortform.api;

import domain.beauty.shortform.domain.RoutineSaveType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "분석한 루틴 저장 요청")
public record ApplyShortformRoutineRequest(
        @NotNull(message = "저장 방식을 선택해 주세요.")
        @Schema(description = "오늘 루틴 또는 보관함", example = "TODAY")
        RoutineSaveType saveType
) {
}
