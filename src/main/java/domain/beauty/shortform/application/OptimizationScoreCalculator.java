package domain.beauty.shortform.application;

import domain.beauty.shortform.client.ProductEnrichmentResult;
import domain.beauty.shortform.domain.OptimizationStatus;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot.OptimizedStep;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.IngredientDetail;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ScoreBreakdown;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.StepResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OptimizationScoreCalculator {

    public RoutineOptimizationSnapshot apply(
            String skinType,
            ShortformAnalysisSnapshot analysis,
            RoutineOptimizationSnapshot optimization,
            Map<Long, InventoryProductEvidence> evidenceByProduct,
            Map<Integer, ScoreHint> hintsByOrder
    ) {
        Map<Integer, StepResult> sourceByOrder = new HashMap<>();
        safe(analysis == null ? null : analysis.steps())
                .forEach(step -> sourceByOrder.putIfAbsent(step.order(), step));
        Map<Long, InventoryProductEvidence> evidence = evidenceByProduct == null
                ? Map.of()
                : evidenceByProduct;
        Map<Integer, ScoreHint> hints = hintsByOrder == null ? Map.of() : hintsByOrder;

        List<Integer> scores = new ArrayList<>();
        Set<String> matchedIngredients = new LinkedHashSet<>();
        Set<String> allergenIngredients = new LinkedHashSet<>();
        for (OptimizedStep optimized : safe(optimization.steps())) {
            StepResult source = sourceByOrder.get(optimized.order());
            ScoreHint hint = hints.get(optimized.order());
            if (optimized.status() == OptimizationStatus.REPLACED) {
                InventoryProductEvidence productEvidence = optimized.productId() == null
                        ? InventoryProductEvidence.unavailable()
                        : evidence.getOrDefault(
                                optimized.productId(), InventoryProductEvidence.unavailable());
                List<ProductEnrichmentResult.Ingredient> ingredients = safe(productEvidence.ingredients());
                scores.add(replacementScore(source, hint, productEvidence.isAvailable(), ingredients));
                matchedIngredients.addAll(validatedNames(
                        hint == null ? List.of() : hint.matchedIngredientNames(),
                        ingredients.stream().map(ProductEnrichmentResult.Ingredient::name).toList()));
                ingredients.stream()
                        .filter(ProductEnrichmentResult.Ingredient::allergen)
                        .map(ProductEnrichmentResult.Ingredient::name)
                        .filter(name -> name != null && !name.isBlank())
                        .map(this::key)
                        .forEach(allergenIngredients::add);
            } else if (source != null) {
                scores.add(clamp(source.matchScore(), 0, 100));
                matchedIngredients.addAll(validatedNames(
                        hint == null ? List.of() : hint.matchedIngredientNames(),
                        safe(source.ingredients()).stream().map(IngredientDetail::name).toList()));
                safe(source.ingredients()).stream()
                        .filter(IngredientDetail::allergen)
                        .map(IngredientDetail::name)
                        .filter(name -> name != null && !name.isBlank())
                        .map(this::key)
                        .forEach(allergenIngredients::add);
            }
        }

        int overallScore = scores.isEmpty()
                ? 0
                : (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0));
        List<String> highlights = PersonalizedHighlights.canonical(
                skinType, matchedIngredients.size(), allergenIngredients.size());
        return new RoutineOptimizationSnapshot(
                overallScore,
                highlights,
                optimization.newProductCount(),
                optimization.replacedCount(),
                optimization.missingCount(),
                optimization.summary(),
                List.copyOf(safe(optimization.steps())));
    }

    private int replacementScore(
            StepResult source,
            ScoreHint hint,
            boolean ingredientAvailable,
            List<ProductEnrichmentResult.Ingredient> ingredients
    ) {
        ScoreBreakdown sourceScore = source == null ? null : source.scoreBreakdown();
        int skinTypeFit = hint == null || hint.skinTypeFit() == null
                ? sourceScore == null ? 20 : clamp(sourceScore.skinTypeFit(), 0, 40)
                : clamp(hint.skinTypeFit(), 0, 40);
        int benefitFit = hint == null || hint.benefitFit() == null
                ? sourceScore == null ? 18 : clamp(sourceScore.benefitFit(), 0, 35)
                : clamp(hint.benefitFit(), 0, 35);
        return skinTypeFit + benefitFit + ingredientSafety(ingredientAvailable, ingredients);
    }

    private int ingredientSafety(
            boolean ingredientAvailable,
            List<ProductEnrichmentResult.Ingredient> ingredients
    ) {
        if (!ingredientAvailable || ingredients.isEmpty()) {
            return 12;
        }
        if (ingredients.stream().anyMatch(item -> item.riskScore() != null && item.riskScore() >= 7)) {
            return 5;
        }
        if (ingredients.stream().anyMatch(item -> item.caution20() || item.allergen())) {
            return 12;
        }
        if (ingredients.stream().anyMatch(item -> item.riskScore() != null && item.riskScore() >= 3)) {
            return 18;
        }
        boolean onlyUnknown = ingredients.stream().allMatch(item -> item.riskScore() == null);
        return onlyUnknown ? 12 : 25;
    }

    private Set<String> validatedNames(List<String> requested, List<String> actual) {
        Set<String> actualKeys = new LinkedHashSet<>();
        safe(actual).stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::key)
                .forEach(actualKeys::add);
        Set<String> result = new LinkedHashSet<>();
        safe(requested).stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::key)
                .filter(actualKeys::contains)
                .forEach(result::add);
        return result;
    }

    private String key(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record ScoreHint(
            Integer skinTypeFit,
            Integer benefitFit,
            List<String> matchedIngredientNames
    ) {
    }
}
