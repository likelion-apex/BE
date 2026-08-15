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
            String displayBrand,
            String displayProductName,
            ProductResolutionStatus productResolutionStatus,
            double productResolutionConfidence,
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
            IngredientDataStatus ingredientDataStatus,
            IngredientVerificationStatus ingredientVerificationStatus,
            String ingredientMarketOrVariant,
            List<IngredientSource> ingredientSources,
            Integer estimatedIngredientCount,
            IngredientStats ingredientStats,
            List<IngredientDetail> ingredients
    ) {
    }

    public record ReasonCard(
            ReasonTone tone,
            AssessmentCategory assessmentCategory,
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

    public record IngredientSource(
            String url,
            String title,
            IngredientSourceType sourceType
    ) {
    }

    public record IngredientDetail(
            int order,
            String name,
            List<String> purposes,
            List<String> skinBenefits,
            Integer riskScore,
            IngredientRiskLevel riskLevel,
            boolean caution20,
            boolean allergen,
            String source,
            boolean regulated,
            String regulationSummary
    ) {
    }

    public record IngredientStats(
            int totalCount,
            int lowRiskCount,
            int moderateRiskCount,
            int highRiskCount,
            int unknownRiskCount,
            int caution20Count,
            int allergenCount
    ) {
    }

    public record AiMetadata(
            String geminiModel,
            String geminiPromptVersion,
            long geminiInputTokens,
            long geminiOutputTokens,
            String productEnrichmentModel,
            String productEnrichmentPromptVersion,
            long productEnrichmentInputTokens,
            long productEnrichmentOutputTokens,
            int productEnrichmentCacheHits,
            int productEnrichmentCacheMisses,
            String openAiModel,
            String openAiPromptVersion,
            long openAiInputTokens,
            long openAiOutputTokens
    ) {
    }
}
