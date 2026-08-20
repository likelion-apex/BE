package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;

import domain.beauty.domain.BeautyRoutineAnalysis.EvidenceSource;
import domain.beauty.domain.BeautyRoutineAnalysis.IdentificationLevel;
import domain.beauty.domain.BeautyRoutineAnalysis.PurposeBasis;
import domain.beauty.domain.BeautyRoutineAnalysis.Step;
import domain.beauty.shortform.application.ShortformAnalysisStateService.InventoryFact;
import domain.beauty.shortform.application.ShortformAnalysisStateService.JobContext;
import domain.beauty.shortform.client.ProductEnrichmentResult;
import domain.beauty.shortform.domain.AssessmentCategory;
import domain.beauty.shortform.domain.IngredientDataStatus;
import domain.beauty.shortform.domain.IngredientVerificationStatus;
import domain.beauty.shortform.domain.ProductResolutionStatus;
import domain.beauty.shortform.domain.SafetyLevel;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.IngredientStats;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ScoreBreakdown;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.StepResult;
import domain.inventory.ProductCategory;
import java.util.List;
import org.junit.jupiter.api.Test;

class OptimizationReasonComposerTest {

    private final OptimizationReasonComposer composer =
            new OptimizationReasonComposer(new KoreanUserCopyNormalizer());

    @Test
    void replacesGenericReasonWithGroundedProductAndIngredientCopy() {
        String reason = composer.forNewAnalysis(
                context(),
                videoStep(),
                analysisStep(),
                ownedProduct(),
                ownedEvidence(),
                "메디힐 앰플은 수분 공급 효과가 있어 대체 가능합니다.");

        assertThat(reason)
                .contains("영상 수분 앰플", "메디힐 티트리 진정 수분 앰플", "히알루론산", "속건조", "추가 구매")
                .doesNotContain("대체 가능합니다", "카테고리", "님");
    }

    @Test
    void keepsDetailedGroundedAiReason() {
        String detailed = "보유하신 메디힐 티트리 진정 수분 앰플에도 히알루론산이 확인되어 속건조를 위한 수분 공급 단계를 추가 구매 없이 이어갈 수 있어요.";

        String reason = composer.forNewAnalysis(
                context(), videoStep(), analysisStep(), ownedProduct(), ownedEvidence(), detailed);

        assertThat(reason).isEqualTo(detailed);
    }

    @Test
    void discardsIngredientThatDoesNotExistInInput() {
        String reason = composer.forNewAnalysis(
                context(),
                videoStep(),
                analysisStep(),
                ownedProduct(),
                ownedEvidence(),
                "보유하신 메디힐 티트리 진정 수분 앰플의 나이아신아마이드 성분으로 속건조를 관리할 수 있어요.");

        assertThat(reason).contains("히알루론산").doesNotContain("나이아신아마이드");
    }

    @Test
    void writesSpecificMissingProductCopyWithoutInternalCategoryReason() {
        String reason = composer.forNewAnalysis(
                context(), videoStep(), analysisStep(), null, null,
                "추천된 인벤토리 제품의 카테고리가 달라 영상 속 제품을 유지합니다.");

        assertThat(reason)
                .contains("영상 수분 앰플", "히알루론산", "속건조", "확인된 보유 제품")
                .doesNotContain("카테고리", "추천된", "님");
    }

    private JobContext context() {
        return new JobContext(
                1L,
                "video",
                "https://youtube.com/watch?v=video",
                "수부지",
                List.of("속건조"),
                List.of(ownedProduct()));
    }

    private InventoryFact ownedProduct() {
        return new InventoryFact(
                100L, 20L, "메디힐 티트리 진정 수분 앰플", "메디힐", "ESSENCE_SERUM", "/owned.png");
    }

    private InventoryProductEvidence ownedEvidence() {
        return new InventoryProductEvidence(
                IngredientVerificationStatus.OFFICIAL,
                List.of(new ProductEnrichmentResult.Ingredient(
                        1, "히알루론산", List.of("보습제"), List.of("수분 공급"), 1, false, false)));
    }

    private MatchedVideoStep videoStep() {
        ProductEnrichmentData enrichment = new ProductEnrichmentData(
                "영상 브랜드",
                "영상 수분 앰플",
                null,
                1,
                IngredientVerificationStatus.OFFICIAL,
                List.of(),
                List.of(new ProductEnrichmentResult.Ingredient(
                        1, "히알루론산", List.of("보습제"), List.of("수분 공급"), 1, false, false)));
        return new MatchedVideoStep(
                new Step(
                        1, "00:01", null, "얼굴", "도포", null, "수분 공급",
                        PurposeBasis.DIRECTLY_STATED, null, IdentificationLevel.EXACT_PRODUCT,
                        "앰플", "영상 브랜드", "영상 수분 앰플", null, "영상 수분 앰플",
                        null, List.of(EvidenceSource.ON_SCREEN_TEXT), "제품명을 확인", 1),
                10L,
                ProductCategory.ESSENCE_SERUM,
                "/video.png",
                "영상 브랜드",
                "영상 수분 앰플",
                ProductResolutionStatus.CATALOG_MATCH,
                1,
                IngredientDataStatus.AVAILABLE,
                enrichment);
    }

    private StepResult analysisStep() {
        return new StepResult(
                1, 1, "00:01", null, "앰플", "영상 브랜드", "영상 수분 앰플",
                "영상 브랜드", "영상 수분 앰플", ProductResolutionStatus.CATALOG_MATCH, 1,
                "/video.png", 10L, 1, "제품명을 확인", 80, "수분 공급", List.of("수분 공급"),
                new ScoreBreakdown(30, 25, 25), SafetyLevel.SAFE, AssessmentCategory.BENEFICIAL,
                "피부 안전도", "안전합니다.", List.of(), IngredientDataStatus.AVAILABLE,
                IngredientVerificationStatus.OFFICIAL, null, List.of(), 1,
                new IngredientStats(1, 1, 0, 0, 0, 0, 0), List.of());
    }
}
