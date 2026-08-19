package domain.routine.dto.response;

import domain.routine.domain.Routine;
import domain.routine.domain.RoutineType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "루틴 보관함 목록 조회 결과 (6.9)")
public record ArchivedRoutineListResponse(
        @Schema(description = "전체 개수") int totalCount,
        @Schema(description = "루틴 목록") List<ArchivedRoutineSummary> routines
) {

    public static ArchivedRoutineListResponse from(List<Routine> routines, Map<Long, Integer> matchScoreByRoutineId) {
        List<ArchivedRoutineSummary> summaries = routines.stream()
                .map(routine -> ArchivedRoutineSummary.from(routine, matchScoreByRoutineId.get(routine.getId())))
                .toList();
        return new ArchivedRoutineListResponse(summaries.size(), summaries);
    }

    @Schema(description = "보관함 루틴 요약 한 건")
    public record ArchivedRoutineSummary(
            @Schema(description = "루틴 ID") Long routineId,
            @Schema(description = "루틴명") String name,
            @Schema(description = "루틴 타입 (DAY/NIGHT)") RoutineType routineType,
            @Schema(description = "단계 수") int stepCount,
            @Schema(description = "생성 일시") LocalDateTime createdAt,
            @Schema(description = "인벤토리 대체를 반영한 최종 매칭 점수. 숏폼 분석 기반 루틴이 아니거나 아직 최적화되지 않았으면 null")
            Integer matchScore
    ) {

        static ArchivedRoutineSummary from(Routine routine, Integer matchScore) {
            return new ArchivedRoutineSummary(
                    routine.getId(), routine.getName(), routine.getRoutineType(),
                    routine.getSteps().size(), routine.getCreatedAt(), matchScore);
        }
    }
}