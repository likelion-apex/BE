package domain.routine.dto.response;

import domain.routine.domain.Routine;
import domain.routine.domain.RoutineType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "루틴 보관함 목록 조회 결과 (6.9)")
public record ArchivedRoutineListResponse(
        @Schema(description = "전체 개수") int totalCount,
        @Schema(description = "루틴 목록") List<ArchivedRoutineSummary> routines
) {

    public static ArchivedRoutineListResponse from(List<Routine> routines) {
        List<ArchivedRoutineSummary> summaries = routines.stream().map(ArchivedRoutineSummary::from).toList();
        return new ArchivedRoutineListResponse(summaries.size(), summaries);
    }

    @Schema(description = "보관함 루틴 요약 한 건")
    public record ArchivedRoutineSummary(
            @Schema(description = "루틴 ID") Long routineId,
            @Schema(description = "루틴명") String name,
            @Schema(description = "루틴 타입 (DAY/NIGHT)") RoutineType routineType,
            @Schema(description = "단계 수") int stepCount,
            @Schema(description = "생성 일시") LocalDateTime createdAt
    ) {

        static ArchivedRoutineSummary from(Routine routine) {
            return new ArchivedRoutineSummary(
                    routine.getId(), routine.getName(), routine.getRoutineType(),
                    routine.getSteps().size(), routine.getCreatedAt());
        }
    }
}