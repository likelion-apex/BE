package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;

import domain.beauty.shortform.domain.AssessmentCategory;
import domain.beauty.shortform.domain.IngredientDataStatus;
import domain.beauty.shortform.domain.IngredientRiskLevel;
import domain.beauty.shortform.domain.IngredientVerificationStatus;
import domain.beauty.shortform.domain.ProductResolutionStatus;
import domain.beauty.shortform.domain.SafetyLevel;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.IngredientDetail;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.IngredientStats;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ReasonCard;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ReasonTone;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ScoreBreakdown;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.StepResult;
import domain.inventory.CategoryImageResolver;
import global.util.PublicUrlResolver;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShortformAnalysisSnapshotNormalizerTest {

    private final KoreanUserCopyNormalizer koreanCopy = new KoreanUserCopyNormalizer();
    private final ShortformAnalysisSnapshotNormalizer normalizer =
            new ShortformAnalysisSnapshotNormalizer(
                    new ReasonCardNormalizer(koreanCopy),
                    new ProductCapacityNormalizer(),
                    new ShortformProductCategoryResolver(),
                    new ShortformProductImageResolver(
                            new CategoryImageResolver(), new PublicUrlResolver("https://mutsa.dev.me.kr")),
                    koreanCopy);

    @Test
    void correctsStoredImageAndEnglishCopyWithoutChangingOfficialIngredientName() {
        IngredientDetail ingredient = new IngredientDetail(
                1, "Lavandula Angustifolia (Lavender) Oil", List.of("Fragrance"), List.of(),
                5, IngredientRiskLevel.MODERATE, false, true, "OFFICIAL", false, null);
        StepResult storedStep = new StepResult(
                1, 1, "00:01", null, "앰플", "브랜드", "수분 앰플", "브랜드", "수분 앰플",
                ProductResolutionStatus.AI_NORMALIZED, 0.9, "https://external.example/wrong.jpg", null,
                0.9, "영상에서 확인", 72, "Hydrating and Soothing",
                List.of("Hydrating and Soothing"), new ScoreBreakdown(25, 22, 25),
                SafetyLevel.CAUTION, AssessmentCategory.CAUTION, "Caution Ingredients",
                "Lavandula Angustifolia Oil, Citrus Aurantium Dulcis Oil은 주의가 필요해요.",
                List.of(new ReasonCard(
                        ReasonTone.POSITIVE, AssessmentCategory.CAUTION, "Caution Ingredients",
                        "Lavandula Angustifolia Oil, Citrus Aurantium Dulcis Oil은 주의가 필요해요.",
                        "AI")),
                IngredientDataStatus.AVAILABLE, IngredientVerificationStatus.OFFICIAL, "한국 / 50ml",
                List.of(), 1, new IngredientStats(1, 0, 1, 0, 0, 0, 1), List.of(ingredient));
        ShortformAnalysisSnapshot stored = new ShortformAnalysisSnapshot(
                "3.0", "video", "https://youtube.com/watch?v=video", "수분 루틴", "스킨케어",
                72, List.of(), "수분 공급", "보습", "요약", List.of(), "안내", List.of(storedStep), null);

        StepResult normalized = normalizer.normalize(stored).steps().getFirst();

        assertThat(normalized.imageUrl())
                .isEqualTo("https://mutsa.dev.me.kr/images/categories/essence_serum.png");
        assertThat(normalized.keyBenefits()).containsExactly("수분 공급");
        assertThat(normalized.matchSummary()).isEqualTo("수분 공급");
        assertThat(normalized.safetyTitle()).isEqualTo("아쉬움·애매");
        assertThat(normalized.safetySummary()).doesNotContain("Lavandula", "Citrus");
        assertThat(normalized.reasons()).allSatisfy(reason -> {
            assertThat(reason.title()).doesNotContain("Caution");
            assertThat(reason.description()).doesNotContain("Lavandula", "Citrus");
        });
        assertThat(normalized.ingredients()).singleElement()
                .extracting(IngredientDetail::name)
                .isEqualTo("Lavandula Angustifolia (Lavender) Oil");
        assertThat(normalized.ingredientMarketOrVariant()).isEqualTo("50ml");
    }
}
