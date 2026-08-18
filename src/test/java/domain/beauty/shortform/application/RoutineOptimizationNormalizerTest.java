package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;

import domain.beauty.shortform.domain.AssessmentCategory;
import domain.beauty.shortform.domain.IngredientDataStatus;
import domain.beauty.shortform.domain.IngredientVerificationStatus;
import domain.beauty.shortform.domain.OptimizationStatus;
import domain.beauty.shortform.domain.ProductResolutionStatus;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot.OptimizedStep;
import domain.beauty.shortform.domain.SafetyLevel;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.IngredientStats;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ScoreBreakdown;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.StepResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RoutineOptimizationNormalizerTest {

    private final RoutineOptimizationNormalizer normalizer =
            new RoutineOptimizationNormalizer(new ShortformProductCategoryResolver());

    @Test
    void readsLegacyCompatibleJsonAndConvertsItToReplacement() throws Exception {
        String legacyJson = """
                {
                  "newProductCount": 0,
                  "compatibleCount": 1,
                  "replacedCount": 0,
                  "missingCount": 0,
                  "summary": "기존 요약",
                  "steps": [{
                    "sourceResultId": 1,
                    "order": 1,
                    "status": "COMPATIBLE",
                    "inventoryId": 100,
                    "productId": 20,
                    "category": "SERUM",
                    "productName": "보유 수분 앰플",
                    "brand": "보유 브랜드",
                    "imageUrl": "/owned.png",
                    "reason": "같은 제품입니다."
                  }]
                }
                """;
        RoutineOptimizationSnapshot stored = new ObjectMapper()
                .readValue(legacyJson, RoutineOptimizationSnapshot.class);

        RoutineOptimizationSnapshot normalized = normalizer.normalize(
                analysis(step("앰플", "영상 수분 앰플", "/video.png", 10L)), stored);

        assertThat(normalized.steps()).singleElement().satisfies(step -> {
            assertThat(step.status()).isEqualTo(OptimizationStatus.REPLACED);
            assertThat(step.productName()).isEqualTo("보유 수분 앰플");
            assertThat(step.replaceName()).isEqualTo("영상 수분 앰플");
        });
        assertThat(normalized.replacedCount()).isEqualTo(1);
        assertThat(normalized.missingCount()).isZero();
    }

    @Test
    void rejectsLegacyReplacementWhenCategoriesDiffer() {
        RoutineOptimizationSnapshot stored = new RoutineOptimizationSnapshot(
                1, 1, 0, "기존 요약",
                List.of(new OptimizedStep(
                        1, 1, OptimizationStatus.REPLACED, 100L, 20L, "SKIN_TONER",
                        "보유 진정 토너", null, "보유 브랜드", "/toner.png", "보습 역할이 비슷합니다.")));

        RoutineOptimizationSnapshot normalized = normalizer.normalize(
                analysis(step("앰플", "영상 수분 앰플", "/ampoule.png", 10L)), stored);

        assertThat(normalized.steps()).singleElement().satisfies(step -> {
            assertThat(step.status()).isEqualTo(OptimizationStatus.VIDEO_PRODUCT);
            assertThat(step.inventoryId()).isNull();
            assertThat(step.productId()).isEqualTo(10L);
            assertThat(step.productName()).isEqualTo("영상 수분 앰플");
            assertThat(step.replaceName()).isNull();
            assertThat(step.imageUrl()).isEqualTo("/ampoule.png");
        });
        assertThat(normalized.replacedCount()).isZero();
        assertThat(normalized.missingCount()).isEqualTo(1);
    }

    private ShortformAnalysisSnapshot analysis(StepResult step) {
        return new ShortformAnalysisSnapshot(
                "3.0", "video", "https://youtube.com/watch?v=video", "수분 루틴", "맞춤",
                80, List.of("수분 공급"), "보습", "히알루론산", "요약", List.of(), "안내",
                List.of(step), null);
    }

    private StepResult step(String category, String productName, String imageUrl, Long productId) {
        return new StepResult(
                1, 1, "00:01", null, category, "영상 브랜드", productName,
                "영상 브랜드", productName, ProductResolutionStatus.CATALOG_MATCH, 1,
                imageUrl, productId, 1, "영상에서 확인", 80, "수분 공급", List.of("수분 공급"),
                new ScoreBreakdown(30, 25, 25), SafetyLevel.SAFE, AssessmentCategory.SAFE,
                "피부 안전도 평가", "안전합니다.", List.of(), IngredientDataStatus.AVAILABLE,
                IngredientVerificationStatus.OFFICIAL, null, List.of(), 1,
                new IngredientStats(1, 1, 0, 0, 0, 0, 0), List.of());
    }
}
