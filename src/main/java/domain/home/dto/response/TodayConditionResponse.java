package domain.home.dto.response;

import domain.routine.domain.DailyCondition;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "오늘의 컨디션 체크 (HOME-01)")
public record TodayConditionResponse(
        @Schema(description = "오늘 기록 여부") boolean logged,
        @Schema(description = "컨디션 한글 라벨 (기록 없으면 null)") String condition,
        @Schema(description = "메모 (기록 없으면 null)") String memo
) {

    private static final TodayConditionResponse NOT_LOGGED = new TodayConditionResponse(false, null, null);

    public static TodayConditionResponse notLogged() {
        return NOT_LOGGED;
    }

    public static TodayConditionResponse from(DailyCondition dailyCondition) {
        return new TodayConditionResponse(true, dailyCondition.getCondition().getLabel(), dailyCondition.getMemo());
    }
}