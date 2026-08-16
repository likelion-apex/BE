package domain.beauty.shortform.api;

import domain.beauty.shortform.domain.RoutineOptimizationSnapshot;
import domain.beauty.shortform.domain.RoutineSaveType;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import domain.beauty.shortform.domain.ShortformAnalysisStatus;
import domain.routine.domain.RoutineStatus;
import domain.routine.domain.RoutineType;
import java.time.LocalDateTime;
import java.util.List;

public final class ShortformAnalysisResponses {

    private ShortformAnalysisResponses() {
    }

    public record Created(
            Long analysisId,
            ShortformAnalysisStatus status,
            int progress,
            boolean reused
    ) {
    }

    public record Status(
            Long analysisId,
            ShortformAnalysisStatus status,
            int progress,
            String message,
            String errorCode,
            String errorMessage,
            LocalDateTime updatedAt
    ) {
    }

    public record History(
            List<HistoryItem> items
    ) {
    }

    public record HistoryItem(
            Long analysisId,
            ShortformAnalysisStatus status,
            String title,
            int stepCount,
            Integer overallScore,
            LocalDateTime createdAt
    ) {
    }

    public record Detail(
            Long analysisId,
            ShortformAnalysisStatus status,
            LocalDateTime createdAt,
            LocalDateTime completedAt,
            ShortformAnalysisSnapshot result
    ) {
    }

    public record ProductDetail(
            Long analysisId,
            ShortformAnalysisSnapshot.StepResult result,
            String disclaimer
    ) {
    }

    public record Optimization(
            Long analysisId,
            LocalDateTime optimizedAt,
            RoutineOptimizationSnapshot result
    ) {
    }

    public record Applied(
            Long analysisId,
            Long routineId,
            RoutineSaveType saveType,
            RoutineType routineType,
            RoutineStatus routineStatus,
            boolean reused
    ) {
    }
}
