package domain.routine.dto.response;

import domain.routine.domain.DailyCondition;
import domain.routine.domain.RoutineLog;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "특정 날짜 기록 조회 결과 (6.8)")
public record DailyLogDetailResponse(
        @Schema(description = "조회한 날짜") LocalDate date,
        @Schema(description = "그날 컨디션 한글 라벨 (기록 없으면 null)") String condition,
        @Schema(description = "컨디션 메모 (기록 없으면 null)") String memo,
        @Schema(description = "그날의 루틴 기록 목록 (없으면 빈 배열)") List<DailyRoutineResponse> routineLogs
) {

    public static DailyLogDetailResponse from(LocalDate date, DailyCondition dailyCondition, List<RoutineLog> logs) {
        String condition = dailyCondition != null ? dailyCondition.getCondition().getLabel() : null;
        String memo = dailyCondition != null ? dailyCondition.getMemo() : null;

        // 6.8은 이번 스코프 밖: aiBriefing 없이 유지(null)
        List<DailyRoutineResponse> routineLogs = logs.stream()
                .map(log -> DailyRoutineResponse.from(log.getRoutine(), log, null))
                .toList();

        return new DailyLogDetailResponse(date, condition, memo, routineLogs);
    }
}