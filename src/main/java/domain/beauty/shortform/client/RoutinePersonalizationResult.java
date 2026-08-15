package domain.beauty.shortform.client;

import domain.beauty.shortform.domain.AssessmentCategory;
import java.util.List;

public record RoutinePersonalizationResult(
        String title,
        String tag,
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
            ScoreBreakdown scoreBreakdown,
            List<String> keyBenefits,
            List<Reason> reasons
    ) {
    }

    public record ScoreBreakdown(int skinTypeFit, int benefitFit) {
    }

    public record Reason(AssessmentCategory assessmentCategory, String title, String description, String evidenceSource) {
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
