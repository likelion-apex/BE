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
            List<Reason> reasons,
            List<String> matchedIngredientNames
    ) {
        public StepAnalysis(
                int order,
                ScoreBreakdown scoreBreakdown,
                List<String> keyBenefits,
                List<Reason> reasons
        ) {
            this(order, scoreBreakdown, keyBenefits, reasons, List.of());
        }
    }

    public record ScoreBreakdown(int skinTypeFit, int benefitFit) {
    }

    public record Reason(AssessmentCategory assessmentCategory, String title, String description, String evidenceSource) {
    }

    public record InventoryRecommendation(
            int order,
            Long inventoryId,
            String reason,
            ScoreBreakdown scoreBreakdown,
            List<String> matchedIngredientNames
    ) {
        public InventoryRecommendation(int order, Long inventoryId, String reason) {
            this(order, inventoryId, reason, null, List.of());
        }
    }

    public record Response(
            RoutinePersonalizationResult analysis,
            String model,
            long inputTokens,
            long outputTokens
    ) {
    }
}
