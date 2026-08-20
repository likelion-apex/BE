package domain.beauty.shortform.application;

import domain.beauty.shortform.domain.AssessmentCategory;
import domain.beauty.shortform.domain.SafetyLevel;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ReasonCard;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.StepResult;
import domain.inventory.ProductCategory;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ShortformAnalysisSnapshotNormalizer {

    private final ReasonCardNormalizer reasonCardNormalizer;
    private final ProductCapacityNormalizer capacityNormalizer;
    private final ShortformProductCategoryResolver categoryResolver;
    private final ShortformProductImageResolver imageResolver;
    private final KoreanUserCopyNormalizer koreanCopy;

    public ShortformAnalysisSnapshotNormalizer(
            ReasonCardNormalizer reasonCardNormalizer,
            ProductCapacityNormalizer capacityNormalizer,
            ShortformProductCategoryResolver categoryResolver,
            ShortformProductImageResolver imageResolver,
            KoreanUserCopyNormalizer koreanCopy
    ) {
        this.reasonCardNormalizer = reasonCardNormalizer;
        this.capacityNormalizer = capacityNormalizer;
        this.categoryResolver = categoryResolver;
        this.imageResolver = imageResolver;
        this.koreanCopy = koreanCopy;
    }

    public ShortformAnalysisSnapshot normalize(ShortformAnalysisSnapshot snapshot) {
        return normalize(snapshot, null, null);
    }

    public ShortformAnalysisSnapshot normalize(
            ShortformAnalysisSnapshot snapshot,
            String nickname,
            String skinType
    ) {
        if (snapshot == null) {
            return null;
        }
        List<StepResult> steps = safe(snapshot.steps()).stream()
                .map(this::normalizeStep)
                .toList();
        return new ShortformAnalysisSnapshot(
                snapshot.schemaVersion(),
                snapshot.videoId(),
                snapshot.youtubeUrl(),
                snapshot.title(),
                snapshot.tag(),
                snapshot.overallScore(),
                PersonalizedHighlights.personalizeAnalysis(
                        nickname, skinType, snapshot),
                snapshot.coreGoal(),
                snapshot.synergyCombo(),
                snapshot.summary(),
                snapshot.warnings(),
                snapshot.disclaimer(),
                steps,
                snapshot.aiMetadata()
        );
    }

    private StepResult normalizeStep(StepResult step) {
        AssessmentCategory primaryCategory = reasonCardNormalizer.resolvePrimary(
                step.primaryAssessmentCategory(), step.ingredientStats(), step.reasons());
        List<ReasonCard> reasons = reasonCardNormalizer.normalize(
                step.reasons(),
                primaryCategory,
                step.ingredientStats(),
                step.ingredients(),
                step.safetySummary());
        String capacity = capacityNormalizer.normalize(
                step.ingredientMarketOrVariant(),
                step.displayProductName(),
                step.productName());
        String productName = textOr(step.displayProductName(), step.productName());
        ProductCategory productCategory = categoryResolver.resolve(step.category(), productName);
        List<String> keyBenefits = koreanCopy.normalizeBenefits(
                step.keyBenefits(), step.category(), productName, categoryResolver);
        ReasonCard primaryReason = reasons.stream()
                .filter(reason -> reason.assessmentCategory() == primaryCategory)
                .findFirst()
                .orElseGet(reasons::getFirst);

        return new StepResult(
                step.resultId(),
                step.order(),
                step.startTime(),
                step.endTime(),
                step.category(),
                step.brand(),
                step.productName(),
                step.displayBrand(),
                step.displayProductName(),
                step.productResolutionStatus(),
                step.productResolutionConfidence(),
                imageResolver.resolve(productCategory),
                step.productId(),
                step.identificationConfidence(),
                step.evidenceSummary(),
                step.matchScore(),
                String.join(" 및 ", keyBenefits),
                keyBenefits,
                step.scoreBreakdown(),
                safetyLevel(primaryCategory),
                primaryCategory,
                categoryTitle(primaryCategory),
                primaryReason.description(),
                reasons,
                step.ingredientDataStatus(),
                step.ingredientVerificationStatus(),
                capacity,
                step.ingredientSources(),
                step.estimatedIngredientCount(),
                step.ingredientStats(),
                step.ingredients()
        );
    }

    private SafetyLevel safetyLevel(AssessmentCategory category) {
        return switch (category) {
            case SAFE, BENEFICIAL -> SafetyLevel.SAFE;
            case CAUTION -> SafetyLevel.CAUTION;
            case WARNING -> SafetyLevel.WARNING;
        };
    }

    private String categoryTitle(AssessmentCategory category) {
        return switch (category) {
            case SAFE -> "성분이 안전함";
            case BENEFICIAL -> "피부에 좋음";
            case CAUTION -> "아쉬움·애매";
            case WARNING -> "경고·위험";
        };
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
