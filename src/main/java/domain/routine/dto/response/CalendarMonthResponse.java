package domain.routine.dto.response;

import domain.routine.domain.RoutineLog;
import domain.routine.domain.RoutineType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Schema(description = "루틴 캘린더 월별 조회 결과 (6.7)")
public record CalendarMonthResponse(
        @Schema(description = "조회 연도") int year,
        @Schema(description = "조회 월") int month,
        @Schema(description = "이 달에 하나 이상 완료 처리된 날짜 수") int completedDaysCount,
        @Schema(description = "날짜별 기록 목록") List<CalendarDayResponse> days
) {

    public static CalendarMonthResponse from(int year, int month, List<RoutineLog> logs) {
        Map<LocalDate, List<RoutineLog>> logsByDate = logs.stream()
                .collect(Collectors.groupingBy(RoutineLog::getLogDate));

        List<CalendarDayResponse> days = logsByDate.entrySet().stream()
                .map(entry -> CalendarDayResponse.from(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CalendarDayResponse::date))
                .toList();

        int completedDaysCount = (int) days.stream().filter(CalendarDayResponse::hasCompletedEntry).count();

        return new CalendarMonthResponse(year, month, completedDaysCount, days);
    }

    @Schema(description = "특정 날짜의 루틴 기록 요약")
    public record CalendarDayResponse(
            @Schema(description = "날짜") LocalDate date,
            @Schema(description = "그날의 루틴별 기록") List<CalendarDayEntry> entries
    ) {

        static CalendarDayResponse from(LocalDate date, List<RoutineLog> logsOfDay) {
            List<CalendarDayEntry> entries = logsOfDay.stream()
                    .map(CalendarDayEntry::from)
                    .toList();
            return new CalendarDayResponse(date, entries);
        }

        boolean hasCompletedEntry() {
            return entries.stream().anyMatch(CalendarDayEntry::completed);
        }
    }

    @Schema(description = "날짜 안의 루틴 기록 한 건")
    public record CalendarDayEntry(
            @Schema(description = "루틴 ID") Long routineId,
            @Schema(description = "루틴 타입 (DAY/NIGHT)") RoutineType routineType,
            @Schema(description = "전체완료 처리 여부") boolean completed
    ) {

        static CalendarDayEntry from(RoutineLog log) {
            return new CalendarDayEntry(log.getRoutine().getId(), log.getRoutine().getRoutineType(), log.isCompleted());
        }
    }
}