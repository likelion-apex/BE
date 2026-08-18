package domain.beauty.shortform.client;

import java.util.List;

public record OptimizationReasonResult(List<StepReason> steps) {
    public record StepReason(
            int order,
            String reason,
            RoutinePersonalizationResult.ScoreBreakdown scoreBreakdown,
            List<String> matchedIngredientNames
    ) {
        public StepReason(int order, String reason) {
            this(order, reason, null, List.of());
        }
    }

    public record Response(
            OptimizationReasonResult result,
            String model,
            long inputTokens,
            long outputTokens
    ) {
    }
}
