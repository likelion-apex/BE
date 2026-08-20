package domain.beauty.shortform.application;

import domain.beauty.shortform.domain.OptimizationStatus;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot.OptimizedStep;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.StepResult;
import domain.inventory.ProductCategory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RoutineOptimizationNormalizer {

    private final ShortformProductCategoryResolver categoryResolver;
    private final ShortformProductImageResolver imageResolver;
    private final KoreanUserCopyNormalizer koreanCopy;

    public RoutineOptimizationNormalizer(
            ShortformProductCategoryResolver categoryResolver,
            ShortformProductImageResolver imageResolver,
            KoreanUserCopyNormalizer koreanCopy
    ) {
        this.categoryResolver = categoryResolver;
        this.imageResolver = imageResolver;
        this.koreanCopy = koreanCopy;
    }

    public RoutineOptimizationSnapshot normalize(
            ShortformAnalysisSnapshot analysis,
            RoutineOptimizationSnapshot optimization
    ) {
        Map<Integer, StepResult> sourceByOrder = new HashMap<>();
        if (analysis != null && analysis.steps() != null) {
            analysis.steps().forEach(step -> sourceByOrder.putIfAbsent(step.order(), step));
        }

        List<OptimizedStep> normalizedSteps = new ArrayList<>();
        List<OptimizedStep> storedSteps = optimization.steps() == null ? List.of() : optimization.steps();
        for (OptimizedStep stored : storedSteps) {
            StepResult source = sourceByOrder.get(stored.order());
            boolean replacement = stored.status() == OptimizationStatus.REPLACED
                    || stored.status() == OptimizationStatus.COMPATIBLE;
            ProductCategory sourceCategory = source == null
                    ? null
                    : categoryResolver.resolve(source.category(), videoProductName(source));
            ProductCategory selectedCategory = categoryResolver.parseStored(stored.category());
            boolean alreadyNormalized = stored.status() == OptimizationStatus.REPLACED
                    && stored.replaceName() != null
                    && !stored.replaceName().isBlank();
            boolean validReplacement = replacement
                    && stored.inventoryId() != null
                    && (alreadyNormalized
                            || (sourceCategory != null && sourceCategory == selectedCategory));

            normalizedSteps.add(validReplacement
                    ? replacementStep(stored, source)
                    : videoProductStep(stored, source, replacement));
        }

        int replacedCount = (int) normalizedSteps.stream()
                .filter(step -> step.status() == OptimizationStatus.REPLACED)
                .count();
        int missingCount = normalizedSteps.size() - replacedCount;
        String summary = replacedCount > 0
                ? "영상 속 제품 중 %d개를 같은 카테고리의 인벤토리 제품으로 교체했습니다.".formatted(replacedCount)
                : "영상 속 루틴과 현재 인벤토리의 조합을 확인했습니다.";
        return new RoutineOptimizationSnapshot(
                optimization.overallScore(),
                optimization.highlights(),
                optimization.newProductCount(),
                replacedCount,
                missingCount,
                summary,
                List.copyOf(normalizedSteps));
    }

    public RoutineOptimizationSnapshot personalize(
            String nickname,
            String skinType,
            ShortformAnalysisSnapshot analysis,
            RoutineOptimizationSnapshot optimization
    ) {
        return new RoutineOptimizationSnapshot(
                optimization.overallScore(),
                PersonalizedHighlights.personalizeOptimization(
                        nickname, skinType, analysis, optimization),
                optimization.newProductCount(),
                optimization.replacedCount(),
                optimization.missingCount(),
                optimization.summary(),
                optimization.steps());
    }

    private OptimizedStep replacementStep(OptimizedStep stored, StepResult source) {
        ProductCategory category = categoryResolver.parseStored(stored.category());
        return new OptimizedStep(
                stored.sourceResultId(),
                stored.order(),
                OptimizationStatus.REPLACED,
                stored.inventoryId(),
                stored.productId(),
                stored.category(),
                stored.productName(),
                textOr(stored.replaceName(), videoProductName(source)),
                stored.brand(),
                imageResolver.resolve(category, stored.imageUrl()),
                koreanCopy.normalizeOptimizationReason(
                        stored.reason(), koreanCopy.optimizationFallback(category, true))
        );
    }

    private OptimizedStep videoProductStep(OptimizedStep stored, StepResult source, boolean rejectedReplacement) {
        ProductCategory category = source == null
                ? categoryResolver.resolve(stored.category(), stored.productName())
                : categoryResolver.resolve(source.category(), videoProductName(source));
        return new OptimizedStep(
                source == null ? stored.sourceResultId() : source.resultId(),
                stored.order(),
                OptimizationStatus.VIDEO_PRODUCT,
                null,
                source == null ? stored.productId() : source.productId(),
                source == null ? stored.category() : source.category(),
                source == null ? stored.productName() : videoProductName(source),
                null,
                source == null ? stored.brand() : source.displayBrand(),
                imageResolver.resolve(category, source == null ? stored.imageUrl() : source.imageUrl()),
                koreanCopy.normalizeOptimizationReason(
                        rejectedReplacement ? null : stored.reason(),
                        koreanCopy.optimizationFallback(category, false))
        );
    }

    private String videoProductName(StepResult source) {
        if (source == null) {
            return null;
        }
        return textOr(source.displayProductName(), textOr(source.productName(), source.category()));
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
