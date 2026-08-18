package domain.beauty.shortform.client;

import java.util.List;

public record OptimizationReasonResult(List<StepReason> steps) {
    public record StepReason(int order, String reason) {
    }

    public record Response(
            OptimizationReasonResult result,
            String model,
            long inputTokens,
            long outputTokens
    ) {
    }
}
