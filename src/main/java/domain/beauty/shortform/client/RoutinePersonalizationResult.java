package domain.beauty.shortform.client;

import domain.beauty.shortform.domain.SafetyLevel;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ReasonTone;
import java.util.List;

public record RoutinePersonalizationResult(
        String title,
        String tag,
        int overallScore,
        List<String> highlights,
        String coreGoal,
        String synergyCombo,
        String summary,
        List<String> warnings,
        List<StepAnalysis> steps,
        List<InventoryRecommendation> inventoryRecommendations
) {
    public record StepAnalysis(
            int order,
            int matchScore,
            String matchSummary,
            SafetyLevel safetyLevel,
            String safetyTitle,
            String safetySummary,
            List<Reason> reasons,
            int estimatedIngredientCount,
            List<Ingredient> ingredients
    ) {
    }

    public record Reason(ReasonTone tone, String title, String description, String evidenceSource) {
    }

    public record Ingredient(String name, List<String> purposes) {
    }

    public record InventoryRecommendation(int order, Long inventoryId, String reason) {
    }

    public record Response(
            RoutinePersonalizationResult analysis,
            String model,
            long inputTokens,
            long outputTokens
    ) {
    }
}
