package domain.beauty.shortform.application;

import domain.beauty.shortform.domain.AssessmentCategory;
import domain.beauty.shortform.domain.IngredientRiskLevel;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.IngredientDetail;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.IngredientStats;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ReasonCard;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ReasonTone;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ReasonCardNormalizer {

    public List<ReasonCard> normalize(
            List<ReasonCard> reasons,
            AssessmentCategory primaryCategory,
            IngredientStats ingredientStats,
            List<IngredientDetail> ingredients,
            String safetySummary
    ) {
        AssessmentCategory resolvedPrimary = resolvePrimary(primaryCategory, ingredientStats, reasons);
        Map<AssessmentCategory, ReasonCard> byCategory = new EnumMap<>(AssessmentCategory.class);
        safe(reasons).stream()
                .filter(Objects::nonNull)
                .map(reason -> normalizeReason(reason, resolvedPrimary))
                .forEach(reason -> byCategory.putIfAbsent(reason.assessmentCategory(), reason));

        byCategory.computeIfAbsent(
                resolvedPrimary,
                category -> fallbackReason(category, ingredientStats, ingredients, safetySummary));
        addEvidenceBackedSecondary(byCategory, ingredientStats, ingredients);

        return List.of(AssessmentCategory.values()).stream()
                .map(byCategory::get)
                .filter(Objects::nonNull)
                .limit(4)
                .toList();
    }

    public ReasonTone toneOf(AssessmentCategory category) {
        if (category == null) {
            return ReasonTone.NEUTRAL;
        }
        return switch (category) {
            case SAFE, BENEFICIAL -> ReasonTone.POSITIVE;
            case CAUTION -> ReasonTone.CAUTION;
            case WARNING -> ReasonTone.WARNING;
        };
    }

    private ReasonCard normalizeReason(ReasonCard reason, AssessmentCategory primaryCategory) {
        AssessmentCategory category = reason.assessmentCategory() == null
                ? primaryCategory
                : reason.assessmentCategory();
        return new ReasonCard(
                toneOf(category),
                category,
                textOr(reason.title(), fallbackTitle(category)),
                textOr(reason.description(), fallbackDescription(category, null)),
                textOr(reason.evidenceSource(), "PERSONALIZED_ANALYSIS")
        );
    }

    public AssessmentCategory resolvePrimary(
            AssessmentCategory primaryCategory,
            IngredientStats stats,
            List<ReasonCard> reasons
    ) {
        if ((stats != null && stats.highRiskCount() > 0)
                || hasCategory(reasons, AssessmentCategory.WARNING)) {
            return AssessmentCategory.WARNING;
        }
        if ((stats != null && (stats.caution20Count() > 0 || stats.allergenCount() > 0))
                || hasCategory(reasons, AssessmentCategory.CAUTION)) {
            return AssessmentCategory.CAUTION;
        }
        if (primaryCategory != null) {
            return primaryCategory;
        }
        if (hasCategory(reasons, AssessmentCategory.BENEFICIAL)) {
            return AssessmentCategory.BENEFICIAL;
        }
        return AssessmentCategory.SAFE;
    }

    private boolean hasCategory(List<ReasonCard> reasons, AssessmentCategory category) {
        return safe(reasons).stream()
                .filter(Objects::nonNull)
                .anyMatch(reason -> reason.assessmentCategory() == category);
    }

    private void addEvidenceBackedSecondary(
            Map<AssessmentCategory, ReasonCard> byCategory,
            IngredientStats stats,
            List<IngredientDetail> ingredients
    ) {
        if (byCategory.size() >= 2) {
            return;
        }
        boolean safeEvidence = stats != null
                && stats.totalCount() > 0
                && stats.highRiskCount() == 0
                && stats.caution20Count() == 0
                && stats.allergenCount() == 0;
        if (safeEvidence && !byCategory.containsKey(AssessmentCategory.SAFE)) {
            byCategory.put(AssessmentCategory.SAFE, fallbackReason(
                    AssessmentCategory.SAFE, stats, ingredients, null));
        }
        if (byCategory.size() < 2
                && !byCategory.containsKey(AssessmentCategory.BENEFICIAL)
                && evidenceIngredient(AssessmentCategory.BENEFICIAL, ingredients) != null) {
            byCategory.put(AssessmentCategory.BENEFICIAL, fallbackReason(
                    AssessmentCategory.BENEFICIAL, stats, ingredients, null));
        }
    }

    private ReasonCard fallbackReason(
            AssessmentCategory category,
            IngredientStats stats,
            List<IngredientDetail> ingredients,
            String safetySummary
    ) {
        IngredientDetail evidence = evidenceIngredient(category, ingredients);
        return new ReasonCard(
                toneOf(category),
                category,
                fallbackTitle(category, evidence),
                fallbackDescription(category, evidence, stats, safetySummary),
                "SERVER_EVIDENCE_FALLBACK"
        );
    }

    private IngredientDetail evidenceIngredient(
            AssessmentCategory category,
            List<IngredientDetail> ingredients
    ) {
        List<IngredientDetail> available = safe(ingredients).stream()
                .filter(Objects::nonNull)
                .filter(ingredient -> ingredient.name() != null && !ingredient.name().isBlank())
                .toList();
        return switch (category) {
            case WARNING -> available.stream()
                    .filter(ingredient -> ingredient.regulated()
                            || ingredient.riskLevel() == IngredientRiskLevel.HIGH)
                    .findFirst()
                    .orElse(null);
            case CAUTION -> available.stream()
                    .filter(ingredient -> ingredient.caution20()
                            || ingredient.allergen()
                            || ingredient.riskLevel() == IngredientRiskLevel.MODERATE)
                    .findFirst()
                    .orElse(null);
            case BENEFICIAL -> available.stream()
                    .filter(ingredient -> ingredient.skinBenefits() != null
                            && !ingredient.skinBenefits().isEmpty())
                    .findFirst()
                    .orElse(null);
            case SAFE -> available.stream()
                    .filter(ingredient -> ingredient.riskLevel() == IngredientRiskLevel.LOW)
                    .findFirst()
                    .orElse(null);
        };
    }

    private String fallbackTitle(AssessmentCategory category, IngredientDetail evidence) {
        if (evidence == null) {
            return fallbackTitle(category);
        }
        return switch (category) {
            case SAFE -> "부담이 적은 " + evidence.name();
            case BENEFICIAL -> evidence.name() + "의 피부 효능";
            case CAUTION -> evidence.name() + " 사용 시 주의";
            case WARNING -> evidence.name() + " 성분 경고";
        };
    }

    private String fallbackTitle(AssessmentCategory category) {
        return switch (category) {
            case SAFE -> "부담이 적은 성분 구성";
            case BENEFICIAL -> "피부 고민에 맞춘 효능";
            case CAUTION -> "사용량과 빈도 조절";
            case WARNING -> "자극 가능 성분 주의";
        };
    }

    private String fallbackDescription(
            AssessmentCategory category,
            IngredientDetail evidence,
            IngredientStats stats,
            String safetySummary
    ) {
        if (evidence != null) {
            return switch (category) {
                case SAFE -> evidence.name() + "은 확인된 정보에서 낮은 위험도로 분류되어 비교적 부담이 적어요.";
                case BENEFICIAL -> evidence.name() + "은 "
                        + String.join(" 및 ", evidence.skinBenefits().stream().limit(2).toList())
                        + "에 도움을 줄 수 있어요.";
                case CAUTION -> cautionDescription(evidence);
                case WARNING -> warningDescription(evidence);
            };
        }
        if (safetySummary != null && !safetySummary.isBlank()) {
            return safetySummary.trim();
        }
        return fallbackDescription(category, stats);
    }

    private String fallbackDescription(AssessmentCategory category, IngredientStats stats) {
        return switch (category) {
            case SAFE -> stats != null && stats.totalCount() > 0
                    ? "확인된 성분에서 고위험 및 알레르기 주의 표시가 확인되지 않았어요."
                    : "실제 제품 라벨을 확인하고 피부 반응을 살피며 사용해 주세요.";
            case BENEFICIAL -> "확인된 제품 효능을 현재 피부 고민에 맞춰 활용할 수 있어요.";
            case CAUTION -> "피부 반응을 살피며 적은 양부터 천천히 사용해 주세요.";
            case WARNING -> "자극 가능성을 줄이기 위해 사용 빈도와 피부 반응을 확인해 주세요.";
        };
    }

    private String cautionDescription(IngredientDetail ingredient) {
        if (ingredient.allergen()) {
            return ingredient.name() + "은 알레르기 유발 가능성이 표시된 성분이므로 소량으로 피부 반응을 확인해 주세요.";
        }
        if (ingredient.caution20()) {
            return ingredient.name() + "은 주의 성분으로 확인되어 사용량과 빈도를 조절하는 것이 좋아요.";
        }
        return ingredient.name() + "은 중간 위험도로 분류되어 피부 반응을 살피며 사용해 주세요.";
    }

    private String warningDescription(IngredientDetail ingredient) {
        if (ingredient.regulated()) {
            return ingredient.name() + "은 규제 정보가 확인된 성분이므로 실제 제품 라벨과 사용 조건을 확인해 주세요.";
        }
        return ingredient.name() + "은 높은 위험도로 분류되어 적은 양부터 사용하고 피부 반응을 확인해 주세요.";
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
