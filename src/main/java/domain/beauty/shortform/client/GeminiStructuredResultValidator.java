package domain.beauty.shortform.client;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class GeminiStructuredResultValidator {

    private GeminiStructuredResultValidator() {
    }

    static RoutinePersonalizationResult validateRoutine(
            RoutinePersonalizationInput input,
            RoutinePersonalizationResult result
    ) {
        if (input == null || result == null
                || blank(result.title()) || blank(result.tag())
                || blank(result.coreGoal()) || blank(result.synergyCombo()) || blank(result.summary())
                || result.highlights() == null || result.highlights().isEmpty()
                || result.warnings() == null
                || result.steps() == null || result.inventoryRecommendations() == null) {
            reject("루틴 분석의 필수 필드가 없습니다.");
        }
        Map<Integer, RoutinePersonalizationInput.VideoStep> videoSteps = uniqueIndex(
                input.steps(), RoutinePersonalizationInput.VideoStep::order, "입력 영상 단계");
        Map<Integer, RoutinePersonalizationResult.StepAnalysis> steps = uniqueIndex(
                result.steps(), RoutinePersonalizationResult.StepAnalysis::order, "분석 단계");
        Map<Integer, RoutinePersonalizationResult.InventoryRecommendation> recommendations = uniqueIndex(
                result.inventoryRecommendations(),
                RoutinePersonalizationResult.InventoryRecommendation::order,
                "인벤토리 추천");
        if (!steps.keySet().equals(videoSteps.keySet()) || !recommendations.keySet().equals(videoSteps.keySet())) {
            reject("입력 단계와 응답 단계가 일치하지 않습니다.");
        }

        Map<Long, RoutinePersonalizationInput.InventoryProduct> inventory = safe(input.inventory()).stream()
                .filter(Objects::nonNull)
                .filter(item -> item.inventoryId() != null)
                .collect(Collectors.toMap(
                        RoutinePersonalizationInput.InventoryProduct::inventoryId,
                        Function.identity(),
                        (left, right) -> left));
        for (RoutinePersonalizationResult.StepAnalysis step : steps.values()) {
            requireScore(step.scoreBreakdown());
            if (step.keyBenefits() == null || step.keyBenefits().isEmpty()
                    || step.reasons() == null || step.reasons().size() < 2
                    || step.matchedIngredientNames() == null) {
                reject("단계별 효능 또는 이유가 없습니다.");
            }
            Set<domain.beauty.shortform.domain.AssessmentCategory> categories = new HashSet<>();
            for (RoutinePersonalizationResult.Reason reason : step.reasons()) {
                if (reason == null || reason.assessmentCategory() == null
                        || blank(reason.title()) || blank(reason.description()) || blank(reason.evidenceSource())
                        || !categories.add(reason.assessmentCategory())) {
                    reject("단계별 평가 카드가 누락되었거나 중복되었습니다.");
                }
            }
            Set<String> allowedIngredients = ingredientNames(videoSteps.get(step.order()).ingredients());
            requireIngredientSubset(step.matchedIngredientNames(), allowedIngredients);
        }
        for (RoutinePersonalizationResult.InventoryRecommendation recommendation : recommendations.values()) {
            if (blank(recommendation.reason()) || recommendation.matchedIngredientNames() == null) {
                reject("인벤토리 추천의 필수 필드가 없습니다.");
            }
            requireScore(recommendation.scoreBreakdown());
            if (recommendation.inventoryId() == null) {
                if (!safe(recommendation.matchedIngredientNames()).isEmpty()) {
                    reject("대체 제품 없이 인벤토리 성분이 반환되었습니다.");
                }
                continue;
            }
            RoutinePersonalizationInput.InventoryProduct selected = inventory.get(recommendation.inventoryId());
            RoutinePersonalizationInput.VideoStep videoStep = videoSteps.get(recommendation.order());
            if (selected == null || !Objects.equals(selected.category(), videoStep.productCategory())) {
                reject("입력에 없거나 카테고리가 다른 인벤토리 제품이 반환되었습니다.");
            }
            requireIngredientSubset(
                    recommendation.matchedIngredientNames(), ingredientNames(selected.ingredients()));
        }
        return result;
    }

    static OptimizationReasonResult validateOptimization(
            OptimizationReasonInput input,
            OptimizationReasonResult result
    ) {
        if (input == null || result == null || result.steps() == null) {
            reject("최적화 이유의 필수 필드가 없습니다.");
        }
        Map<Integer, OptimizationReasonInput.Step> inputSteps = uniqueIndex(
                input.steps(), OptimizationReasonInput.Step::order, "최적화 입력 단계");
        Map<Integer, OptimizationReasonResult.StepReason> reasons = uniqueIndex(
                result.steps(),
                OptimizationReasonResult.StepReason::order,
                "최적화 이유");
        if (!reasons.keySet().equals(inputSteps.keySet())) {
            reject("입력 단계와 최적화 이유 단계가 일치하지 않습니다.");
        }
        for (OptimizationReasonResult.StepReason reason : reasons.values()) {
            if (blank(reason.reason()) || reason.matchedIngredientNames() == null) {
                reject("제품별 최적화 이유의 필수 필드가 없습니다.");
            }
            requireScore(reason.scoreBreakdown());
            OptimizationReasonInput.Step inputStep = inputSteps.get(reason.order());
            OptimizationReasonInput.Product selected = inputStep.status()
                    == domain.beauty.shortform.domain.OptimizationStatus.REPLACED
                    && inputStep.inventoryProduct() != null
                    ? inputStep.inventoryProduct()
                    : inputStep.videoProduct();
            requireIngredientSubset(
                    reason.matchedIngredientNames(),
                    selected == null ? Set.of() : ingredientNames(selected.ingredients()));
        }
        return result;
    }

    private static void requireScore(RoutinePersonalizationResult.ScoreBreakdown score) {
        if (score == null
                || score.skinTypeFit() < 0 || score.skinTypeFit() > 40
                || score.benefitFit() < 0 || score.benefitFit() > 35) {
            reject("점수 범위가 올바르지 않습니다.");
        }
    }

    private static Set<String> ingredientNames(List<? extends Object> ingredients) {
        Set<String> names = new HashSet<>();
        for (Object ingredient : safe(ingredients)) {
            String name = switch (ingredient) {
                case RoutinePersonalizationInput.Ingredient value -> value.name();
                case OptimizationReasonInput.Ingredient value -> value.name();
                default -> null;
            };
            if (!blank(name)) {
                names.add(normalize(name));
            }
        }
        return names;
    }

    private static void requireIngredientSubset(List<String> actual, Set<String> allowed) {
        boolean invalid = safe(actual).stream()
                .filter(Objects::nonNull)
                .map(GeminiStructuredResultValidator::normalize)
                .anyMatch(value -> !allowed.contains(value));
        if (invalid) {
            reject("입력에 없는 성분명이 반환되었습니다.");
        }
    }

    private static <T> Map<Integer, T> uniqueIndex(
            List<T> values,
            Function<T, Integer> order,
            String label
    ) {
        List<T> safeValues = safe(values).stream().filter(Objects::nonNull).toList();
        Map<Integer, T> indexed = safeValues.stream().collect(Collectors.toMap(
                order,
                Function.identity(),
                (left, right) -> left));
        if (indexed.size() != safeValues.size()) {
            reject(label + "의 순서가 중복되었습니다.");
        }
        return indexed;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static void reject(String message) {
        throw new GeminiCandidateRejectedException(message);
    }
}
