package domain.beauty.shortform.domain;

import java.util.List;

public record ShortformAnalysisSnapshot(
        String schemaVersion,
        String videoId,
        String youtubeUrl,
        String title,
        String tag,
        int overallScore,
        List<String> highlights,
        String coreGoal,
        String synergyCombo,
        String summary,
        List<String> warnings,
        String disclaimer,
        List<StepResult> steps,
        AiMetadata aiMetadata
) {

    public record StepResult(
            long resultId,
            int order,
            String startTime,
            String endTime,
            String category,
            String brand,
            String productName,
            String imageUrl,
            Long productId,
            double identificationConfidence,
            String evidenceSummary,
            int matchScore,
            String matchSummary,
            SafetyLevel safetyLevel,
            String safetyTitle,
            String safetySummary,
            List<ReasonCard> reasons,
            int estimatedIngredientCount,
            List<IngredientDetail> ingredients
    ) {
    }

    public record ReasonCard(
            ReasonTone tone,
            String title,
            String description,
            String evidenceSource
    ) {
    }

    public enum ReasonTone {
        POSITIVE,
        NEUTRAL,
        CAUTION,
        WARNING
    }

    public record IngredientDetail(
            String name,
            List<String> purposes,
            String source,
            boolean regulated,
            String regulationSummary
    ) {
    }

    public record AiMetadata(
            String geminiModel,
            String geminiPromptVersion,
            long geminiInputTokens,
            long geminiOutputTokens,
            String openAiModel,
            String openAiPromptVersion,
            long openAiInputTokens,
            long openAiOutputTokens
    ) {
    }
}
