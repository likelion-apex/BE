package domain.home.dto.request;

import domain.routine.domain.DailyConditionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "오늘의 컨디션 기록/취소 요청")
public record DailyConditionRequest(
        @Schema(description = "컨디션 (null이면 오늘 기록 취소)") DailyConditionType condition,
        @Schema(description = "메모 (최대 30자)") @Size(max = 30) String memo
) {
}