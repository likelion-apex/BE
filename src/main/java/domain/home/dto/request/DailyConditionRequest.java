package domain.home.dto.request;

import domain.routine.domain.DailyConditionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "오늘의 컨디션 기록/취소 요청")
public record DailyConditionRequest(
        @Schema(
                description = "컨디션 (null이면 오늘 기록 취소)",
                allowableValues = {"트러블있고예민해요", "건조하고푸석해요", "평범하고무난해요", "촉촉하고편안해요", "컨디션최고예요"}
        ) DailyConditionType condition,
        @Schema(description = "메모 (최대 30자)") @Size(max = 30) String memo
) {
}