package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;

import domain.beauty.shortform.domain.AssessmentCategory;
import domain.beauty.shortform.domain.IngredientRiskLevel;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.IngredientDetail;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.IngredientStats;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ReasonCard;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ReasonTone;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReasonCardNormalizerTest {

    private final ReasonCardNormalizer normalizer =
            new ReasonCardNormalizer(new KoreanUserCopyNormalizer());

    @Test
    void addsMissingCautionCardAndKeepsFourLevelOrder() {
        IngredientDetail caution = ingredient(
                "향료", IngredientRiskLevel.MODERATE, true, false, List.of());
        List<ReasonCard> normalized = normalizer.normalize(
                List.of(
                        reason(AssessmentCategory.BENEFICIAL, "수분 공급"),
                        reason(AssessmentCategory.SAFE, "낮은 위험도")),
                AssessmentCategory.CAUTION,
                new IngredientStats(1, 0, 1, 0, 0, 1, 0),
                List.of(caution),
                "사용 시 주의해 주세요.");

        assertThat(normalized)
                .extracting(ReasonCard::assessmentCategory)
                .containsExactly(
                        AssessmentCategory.SAFE,
                        AssessmentCategory.BENEFICIAL,
                        AssessmentCategory.CAUTION);
        assertThat(normalized.getLast().tone()).isEqualTo(ReasonTone.CAUTION);
        assertThat(normalized.getLast().description()).contains("향료", "사용량과 빈도");
    }

    @Test
    void highRiskEvidenceOverridesStalePositivePrimary() {
        IngredientDetail highRisk = ingredient(
                "테스트 고위험 성분", IngredientRiskLevel.HIGH, false, false, List.of());
        AssessmentCategory primary = normalizer.resolvePrimary(
                AssessmentCategory.BENEFICIAL,
                new IngredientStats(1, 0, 0, 1, 0, 0, 0),
                List.of(reason(AssessmentCategory.BENEFICIAL, "보습")));
        List<ReasonCard> normalized = normalizer.normalize(
                List.of(reason(AssessmentCategory.BENEFICIAL, "보습")),
                primary,
                new IngredientStats(1, 0, 0, 1, 0, 0, 0),
                List.of(highRisk),
                null);

        assertThat(primary).isEqualTo(AssessmentCategory.WARNING);
        assertThat(normalized).anySatisfy(reason -> {
            assertThat(reason.assessmentCategory()).isEqualTo(AssessmentCategory.WARNING);
            assertThat(reason.tone()).isEqualTo(ReasonTone.WARNING);
            assertThat(reason.description()).contains("테스트 고위험 성분");
        });
    }

    @Test
    void normalizesNullCategoryBeforeToneAndDoesNotInventRisk() {
        List<ReasonCard> normalized = normalizer.normalize(
                List.of(new ReasonCard(
                        ReasonTone.NEUTRAL, null, "피부 진정", "진정에 도움을 줄 수 있어요.", null)),
                AssessmentCategory.BENEFICIAL,
                null,
                List.of(),
                null);

        assertThat(normalized).singleElement().satisfies(reason -> {
            assertThat(reason.assessmentCategory()).isEqualTo(AssessmentCategory.BENEFICIAL);
            assertThat(reason.tone()).isEqualTo(ReasonTone.POSITIVE);
        });
        assertThat(normalized)
                .noneMatch(reason -> reason.assessmentCategory() == AssessmentCategory.CAUTION
                        || reason.assessmentCategory() == AssessmentCategory.WARNING);
    }

    @Test
    void deduplicatesCategoriesAndReturnsAtMostFourCards() {
        List<ReasonCard> normalized = normalizer.normalize(
                List.of(
                        reason(AssessmentCategory.WARNING, "첫 경고"),
                        reason(AssessmentCategory.SAFE, "안전"),
                        reason(AssessmentCategory.BENEFICIAL, "효능"),
                        reason(AssessmentCategory.CAUTION, "주의"),
                        reason(AssessmentCategory.WARNING, "중복 경고")),
                AssessmentCategory.WARNING,
                null,
                List.of(),
                null);

        assertThat(normalized).hasSize(4);
        assertThat(normalized)
                .extracting(ReasonCard::assessmentCategory)
                .containsExactly(
                        AssessmentCategory.SAFE,
                        AssessmentCategory.BENEFICIAL,
                        AssessmentCategory.CAUTION,
                        AssessmentCategory.WARNING);
        assertThat(normalized.getLast().title()).isEqualTo("첫 경고");
    }

    private ReasonCard reason(AssessmentCategory category, String title) {
        return new ReasonCard(
                ReasonTone.POSITIVE, category, title, title + "에 대한 근거입니다.", "TEST");
    }

    private IngredientDetail ingredient(
            String name,
            IngredientRiskLevel riskLevel,
            boolean caution20,
            boolean allergen,
            List<String> skinBenefits
    ) {
        return new IngredientDetail(
                1, name, List.of("배합 목적"), skinBenefits, 5, riskLevel,
                caution20, allergen, "TEST", false, null);
    }
}
