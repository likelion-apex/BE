package domain.beauty.shortform.application;

import domain.beauty.shortform.domain.AssessmentCategory;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.IngredientDetail;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.StepResult;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PersonalizedHighlights {

    private static final Pattern MATCHED_PATTERN = Pattern.compile(
            "^(?:.+?님\\s+)?(.+?)\\s+맞춤 성분\\s+(\\d+)개 매칭$");
    private static final Pattern ALLERGEN_PATTERN = Pattern.compile(
            "^(?:.+?님\\s+피부\\s+)?알레르기 유발 성분\\s+(\\d+)개$");

    private PersonalizedHighlights() {
    }

    static List<String> calculate(
            String skinType,
            List<StepResult> steps,
            Map<Integer, List<String>> matchedNamesByOrder
    ) {
        Set<String> matched = new LinkedHashSet<>();
        Set<String> allergens = new LinkedHashSet<>();
        Map<Integer, List<String>> requested = matchedNamesByOrder == null
                ? Map.of()
                : matchedNamesByOrder;

        for (StepResult step : safe(steps)) {
            Map<String, IngredientDetail> actual = new LinkedHashMap<>();
            for (IngredientDetail ingredient : safe(step.ingredients())) {
                if (ingredient == null || blank(ingredient.name())) {
                    continue;
                }
                String key = key(ingredient.name());
                actual.putIfAbsent(key, ingredient);
                if (ingredient.allergen()) {
                    allergens.add(key);
                }
            }
            for (String candidate : safe(requested.get(step.order()))) {
                if (!blank(candidate) && actual.containsKey(key(candidate))) {
                    matched.add(key(candidate));
                }
            }
        }
        return canonical(skinType, matched.size(), allergens.size());
    }

    static List<String> personalizeAnalysis(
            String nickname,
            String skinType,
            ShortformAnalysisSnapshot snapshot
    ) {
        Counts stored = parse(snapshot == null ? null : snapshot.highlights());
        Counts counts = stored == null ? legacyCounts(snapshot) : stored;
        String label = stored == null ? skinType : stored.skinType();
        return personalized(nickname, label, counts.matchedCount(), counts.allergenCount());
    }

    static List<String> personalizeOptimization(
            String nickname,
            String skinType,
            ShortformAnalysisSnapshot analysis,
            RoutineOptimizationSnapshot optimization
    ) {
        Counts stored = parse(optimization == null ? null : optimization.highlights());
        if (stored != null) {
            return personalized(
                    nickname, stored.skinType(), stored.matchedCount(), stored.allergenCount());
        }

        Map<Integer, StepResult> sourceByOrder = new LinkedHashMap<>();
        safe(analysis == null ? null : analysis.steps())
                .forEach(step -> sourceByOrder.putIfAbsent(step.order(), step));
        Set<Integer> videoOrders = new LinkedHashSet<>();
        safe(optimization == null ? null : optimization.steps()).stream()
                .filter(step -> step.status() != null && step.status().name().equals("VIDEO_PRODUCT"))
                .forEach(step -> videoOrders.add(step.order()));
        List<StepResult> groundedVideoSteps = videoOrders.stream()
                .map(sourceByOrder::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        Counts counts = legacyCounts(new ShortformAnalysisSnapshot(
                analysis == null ? null : analysis.schemaVersion(),
                analysis == null ? null : analysis.videoId(),
                analysis == null ? null : analysis.youtubeUrl(),
                analysis == null ? null : analysis.title(),
                analysis == null ? null : analysis.tag(),
                analysis == null ? 0 : analysis.overallScore(),
                List.of(),
                analysis == null ? null : analysis.coreGoal(),
                analysis == null ? null : analysis.synergyCombo(),
                analysis == null ? null : analysis.summary(),
                analysis == null ? List.of() : analysis.warnings(),
                analysis == null ? null : analysis.disclaimer(),
                groundedVideoSteps,
                analysis == null ? null : analysis.aiMetadata()));
        return personalized(nickname, skinType, counts.matchedCount(), counts.allergenCount());
    }

    static List<String> canonical(String skinType, int matchedCount, int allergenCount) {
        String label = profileLabel(skinType);
        return List.of(
                "%s 맞춤 성분 %d개 매칭".formatted(label, Math.max(0, matchedCount)),
                "알레르기 유발 성분 %d개".formatted(Math.max(0, allergenCount)));
    }

    private static Counts legacyCounts(ShortformAnalysisSnapshot snapshot) {
        Set<String> matched = new LinkedHashSet<>();
        Set<String> allergens = new LinkedHashSet<>();
        for (StepResult step : safe(snapshot == null ? null : snapshot.steps())) {
            String beneficialEvidence = safe(step.reasons()).stream()
                    .filter(reason -> reason != null
                            && reason.assessmentCategory() == AssessmentCategory.BENEFICIAL)
                    .map(reason -> text(reason.title()) + " " + text(reason.description()))
                    .reduce((left, right) -> left + " " + right)
                    .orElse("");
            String normalizedEvidence = key(beneficialEvidence);
            for (IngredientDetail ingredient : safe(step.ingredients())) {
                if (ingredient == null || blank(ingredient.name())) {
                    continue;
                }
                String ingredientKey = key(ingredient.name());
                if (!ingredientKey.isBlank() && normalizedEvidence.contains(ingredientKey)) {
                    matched.add(ingredientKey);
                }
                if (ingredient.allergen()) {
                    allergens.add(ingredientKey);
                }
            }
        }
        return new Counts(null, matched.size(), allergens.size());
    }

    private static Counts parse(List<String> highlights) {
        if (highlights == null || highlights.size() < 2) {
            return null;
        }
        Matcher matched = MATCHED_PATTERN.matcher(text(highlights.get(0)).trim());
        Matcher allergen = ALLERGEN_PATTERN.matcher(text(highlights.get(1)).trim());
        if (!matched.matches() || !allergen.matches()) {
            return null;
        }
        return new Counts(
                matched.group(1).trim(),
                Integer.parseInt(matched.group(2)),
                Integer.parseInt(allergen.group(1)));
    }

    private static List<String> personalized(
            String nickname,
            String skinType,
            int matchedCount,
            int allergenCount
    ) {
        String owner = owner(nickname);
        String prefix = owner.isBlank() ? "" : owner + " ";
        return List.of(
                "%s%s 맞춤 성분 %d개 매칭".formatted(
                        prefix, profileLabel(skinType), Math.max(0, matchedCount)),
                "%s피부 알레르기 유발 성분 %d개".formatted(
                        prefix, Math.max(0, allergenCount)));
    }

    private static String owner(String nickname) {
        if (blank(nickname)) {
            return "";
        }
        String value = nickname.trim();
        return value.endsWith("님") ? value : value + "님";
    }

    private static String profileLabel(String skinType) {
        return blank(skinType) ? "피부" : skinType.trim();
    }

    private static String key(String value) {
        return text(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record Counts(String skinType, int matchedCount, int allergenCount) {
    }
}
