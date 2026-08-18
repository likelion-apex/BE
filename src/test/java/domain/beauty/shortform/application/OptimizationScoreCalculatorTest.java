package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;

import domain.beauty.shortform.application.OptimizationScoreCalculator.ScoreHint;
import domain.beauty.shortform.client.ProductEnrichmentResult;
import domain.beauty.shortform.domain.IngredientVerificationStatus;
import domain.beauty.shortform.domain.OptimizationStatus;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot.OptimizedStep;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OptimizationScoreCalculatorTest {

    private final OptimizationScoreCalculator calculator = new OptimizationScoreCalculator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void keepsOriginalScoresWhenEveryStepUsesVideoProduct() throws Exception {
        ShortformAnalysisSnapshot analysis = readAnalysis("""
                [
                  {
                    "resultId":1,
                    "order":1,
                    "productResolutionConfidence":1,
                    "identificationConfidence":1,
                    "matchScore":70,
                    "scoreBreakdown":{"skinTypeFit":28,"benefitFit":24,"ingredientSafety":18},
                    "ingredientStats":{"totalCount":1,"lowRiskCount":1,"moderateRiskCount":0,"highRiskCount":0,"unknownRiskCount":0,"caution20Count":0,"allergenCount":0},
                    "ingredients":[{"order":1,"name":"히알루론산","purposes":[],"skinBenefits":[],"riskScore":1,"riskLevel":"LOW","caution20":false,"allergen":false,"regulated":false}]
                  },
                  {
                    "resultId":2,
                    "order":2,
                    "productResolutionConfidence":1,
                    "identificationConfidence":1,
                    "matchScore":81,
                    "scoreBreakdown":{"skinTypeFit":32,"benefitFit":24,"ingredientSafety":25},
                    "ingredientStats":{"totalCount":1,"lowRiskCount":0,"moderateRiskCount":1,"highRiskCount":0,"unknownRiskCount":0,"caution20Count":0,"allergenCount":1},
                    "ingredients":[{"order":1,"name":"향료","purposes":[],"skinBenefits":[],"riskScore":4,"riskLevel":"MODERATE","caution20":false,"allergen":true,"regulated":false}]
                  }
                ]
                """);
        RoutineOptimizationSnapshot optimization = new RoutineOptimizationSnapshot(
                2, 0, 2, "영상 제품을 유지합니다.", List.of(
                        videoStep(1, 1),
                        videoStep(2, 2)));

        RoutineOptimizationSnapshot scored = calculator.apply(
                "수부지",
                analysis,
                optimization,
                Map.of(),
                Map.of(
                        1, new ScoreHint(40, 35, List.of("히알루론산", " 히알루론산 ", "입력에 없는 성분")),
                        2, new ScoreHint(0, 0, List.of("히알루론산"))));

        assertThat(scored.overallScore()).isEqualTo(76);
        assertThat(scored.highlights()).containsExactly(
                "수부지 맞춤 성분 1개 매칭",
                "알레르기 유발 성분 1개");
    }

    @Test
    void recalculatesReplacementWithClampedAiFitAndVerifiedIngredientSafety() throws Exception {
        ShortformAnalysisSnapshot analysis = readAnalysis("""
                [{
                  "resultId":1,
                  "order":1,
                  "productResolutionConfidence":1,
                  "identificationConfidence":1,
                  "matchScore":80,
                  "scoreBreakdown":{"skinTypeFit":30,"benefitFit":25,"ingredientSafety":25},
                  "ingredients":[]
                }]
                """);
        RoutineOptimizationSnapshot optimization = new RoutineOptimizationSnapshot(
                0, 1, 0, "대체했습니다.", List.of(new OptimizedStep(
                        1, 1, OptimizationStatus.REPLACED, 100L, 20L, "SERUM",
                        "보유 앰플", "영상 앰플", "브랜드", "/owned.png", "대체 이유")));
        InventoryProductEvidence evidence = new InventoryProductEvidence(
                IngredientVerificationStatus.CORROBORATED,
                List.of(
                        ingredient("히알루론산", 1, false),
                        ingredient("향료", 4, true)));

        RoutineOptimizationSnapshot scored = calculator.apply(
                "민감성",
                analysis,
                optimization,
                Map.of(20L, evidence),
                Map.of(1, new ScoreHint(100, -5, List.of("히알루론산", "가짜 성분"))));

        assertThat(scored.overallScore()).isEqualTo(52);
        assertThat(scored.highlights()).containsExactly(
                "민감성 맞춤 성분 1개 매칭",
                "알레르기 유발 성분 1개");
    }

    private ShortformAnalysisSnapshot readAnalysis(String steps) throws Exception {
        return objectMapper.readValue("""
                {
                  "schemaVersion":"3.0",
                  "videoId":"video",
                  "youtubeUrl":"https://youtube.com/watch?v=video",
                  "title":"루틴",
                  "tag":"맞춤",
                  "overallScore":0,
                  "highlights":[],
                  "coreGoal":"관리",
                  "synergyCombo":"조합",
                  "summary":"요약",
                  "warnings":[],
                  "disclaimer":"안내",
                  "steps":%s,
                  "aiMetadata":null
                }
                """.formatted(steps), ShortformAnalysisSnapshot.class);
    }

    private OptimizedStep videoStep(long sourceResultId, int order) {
        return new OptimizedStep(
                sourceResultId, order, OptimizationStatus.VIDEO_PRODUCT, null, sourceResultId,
                "SERUM", "영상 제품", null, "영상 브랜드", "/video.png", "대체품 없음");
    }

    private ProductEnrichmentResult.Ingredient ingredient(
            String name,
            Integer riskScore,
            boolean allergen
    ) {
        return new ProductEnrichmentResult.Ingredient(
                1, name, List.of(), List.of(), riskScore, false, allergen);
    }
}
