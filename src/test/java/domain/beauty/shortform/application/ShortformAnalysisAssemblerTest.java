package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import domain.beauty.domain.BeautyRoutineAnalysis.EvidenceSource;
import domain.beauty.domain.BeautyRoutineAnalysis.IdentificationLevel;
import domain.beauty.domain.BeautyRoutineAnalysis.PurposeBasis;
import domain.beauty.domain.BeautyRoutineAnalysis.Step;
import domain.beauty.shortform.application.ShortformAnalysisAssembler.AssembledResult;
import domain.beauty.shortform.application.ShortformAnalysisStateService.InventoryFact;
import domain.beauty.shortform.application.ShortformAnalysisStateService.JobContext;
import domain.beauty.shortform.client.RoutinePersonalizationResult;
import domain.beauty.shortform.client.ProductEnrichmentResult;
import domain.beauty.shortform.client.RoutinePersonalizationResult.Response;
import domain.beauty.shortform.config.OpenAiRoutineProperties;
import domain.beauty.shortform.domain.OptimizationStatus;
import domain.beauty.shortform.domain.AssessmentCategory;
import domain.beauty.shortform.domain.IngredientDataStatus;
import domain.beauty.shortform.domain.IngredientSourceType;
import domain.beauty.shortform.domain.IngredientVerificationStatus;
import domain.beauty.shortform.domain.ProductResolutionStatus;
import domain.beauty.shortform.domain.SafetyLevel;
import domain.beauty.shortform.domain.VideoRoutineExtraction;
import domain.cosmetic.cache.RegulationInfoCache;
import domain.cosmetic.client.CsmtcsReglMaterialClient;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShortformAnalysisAssemblerTest {

    private final RegulationInfoCache regulationInfoCache =
            new RegulationInfoCache(mock(CsmtcsReglMaterialClient.class));
    private final ShortformAnalysisAssembler assembler =
            new ShortformAnalysisAssembler(regulationInfoCache, new OpenAiRoutineProperties());

    @Test
    void forcesUnknownForCategoryOnlyStepAndIgnoresUnknownInventoryId() {
        JobContext context = new JobContext(
                1L,
                "t1S24pgO2XQ",
                "https://www.youtube.com/watch?v=t1S24pgO2XQ",
                "테스터",
                "수부지",
                List.of("민감성"),
                List.of(new InventoryFact(100L, 20L, "진정 토너", "테스트", "TONER", null))
        );
        ProductEnrichmentData exactEnrichment = new ProductEnrichmentData(
                "라운드랩",
                "1025 독도 토너",
                "한국 판매 처방",
                0.96,
                IngredientVerificationStatus.OFFICIAL,
                List.of(new ProductEnrichmentResult.Source(
                        "https://roundlab.com/products/1025-dokdo-toner",
                        "1025 Dokdo Toner",
                        IngredientSourceType.OFFICIAL)),
                List.of(new ProductEnrichmentResult.Ingredient(
                        1, "판테놀", List.of("보습제"), List.of("피부 보습"), 1, false, false))
        );
        MatchedVideoStep exact = new MatchedVideoStep(
                step(1, IdentificationLevel.EXACT_PRODUCT, "독도 토너"),
                10L, null, "라운드랩", "1025 독도 토너", ProductResolutionStatus.CATALOG_MATCH,
                1, IngredientDataStatus.AVAILABLE, exactEnrichment);
        MatchedVideoStep categoryOnly = new MatchedVideoStep(
                step(2, IdentificationLevel.CATEGORY_ONLY, null),
                null, null, null, "토너", ProductResolutionStatus.UNRESOLVED,
                0, IngredientDataStatus.NOT_ELIGIBLE, ProductEnrichmentData.unresolved());

        RoutinePersonalizationResult result = new RoutinePersonalizationResult(
                "수부지 진정 루틴",
                "민감 피부 맞춤",
                82,
                List.of("보습 단계가 균형적입니다."),
                "장벽 보호",
                "판테놀 + 히알루론산",
                "단계별 사용을 권장합니다.",
                List.of(),
                List.of(aiStep(1, SafetyLevel.SAFE), aiStep(2, SafetyLevel.SAFE)),
                List.of(
                        new RoutinePersonalizationResult.InventoryRecommendation(1, 100L, "토너 단계 대체"),
                        new RoutinePersonalizationResult.InventoryRecommendation(2, 999L, "존재하지 않는 추천")
                )
        );
        VideoRoutineExtraction extraction = new VideoRoutineExtraction(
                "cache", "t1S24pgO2XQ", context.youtubeUrl(), "gemini-test", "1.0", "{}", 10, 5);

        AssembledResult assembled = assembler.assemble(
                context,
                List.of(exact, categoryOnly),
                new Response(result, "gpt-test", 20, 10),
                extraction,
                new ShortformProductEnrichmentService.BatchResult(
                        java.util.Map.of(1, exactEnrichment), "gpt-test", "1.0", 40, 20, 0, 1));

        assertThat(assembled.analysis().steps().get(0).safetyLevel()).isEqualTo(SafetyLevel.SAFE);
        assertThat(assembled.analysis().steps().get(1).safetyLevel()).isEqualTo(SafetyLevel.UNKNOWN);
        assertThat(assembled.analysis().steps().get(1).ingredients()).isEmpty();
        assertThat(assembled.analysis().steps().get(1).estimatedIngredientCount()).isNull();
        assertThat(assembled.analysis().steps().get(0).ingredientStats().totalCount()).isEqualTo(1);
        assertThat(assembled.optimization().steps().get(0).status()).isEqualTo(OptimizationStatus.REPLACED);
        assertThat(assembled.optimization().steps().get(1).status()).isEqualTo(OptimizationStatus.NO_INVENTORY_MATCH);
        assertThat(assembled.optimization().replacedCount()).isEqualTo(1);
        assertThat(assembled.optimization().missingCount()).isEqualTo(1);
    }

    @Test
    void doesNotPresentEstimatedOrHighRiskIngredientEvidenceAsUnconditionallySafe() {
        JobContext context = new JobContext(
                1L, "video", "https://www.youtube.com/watch?v=video", "테스터", "건성",
                List.of(), List.of());
        ProductEnrichmentData estimatedEnrichment = enrichment(
                IngredientVerificationStatus.ESTIMATED,
                new ProductEnrichmentResult.Ingredient(
                        1, "정제수", List.of("용제"), List.of("수분 공급"), 1, false, false));
        ProductEnrichmentData highRiskEnrichment = enrichment(
                IngredientVerificationStatus.OFFICIAL,
                new ProductEnrichmentResult.Ingredient(
                        1, "테스트 고위험 성분", List.of("향료"), List.of(), 8, true, true));
        MatchedVideoStep estimated = new MatchedVideoStep(
                step(1, IdentificationLevel.CATEGORY_ONLY, null),
                null, null, "추정 브랜드", "추정 수딩 크림", ProductResolutionStatus.AI_NORMALIZED,
                0.72, IngredientDataStatus.AVAILABLE, estimatedEnrichment);
        MatchedVideoStep highRisk = new MatchedVideoStep(
                step(2, IdentificationLevel.EXACT_PRODUCT, "테스트 크림"),
                null, null, "테스트", "테스트 크림", ProductResolutionStatus.AI_NORMALIZED,
                0.95, IngredientDataStatus.AVAILABLE, highRiskEnrichment);
        RoutinePersonalizationResult result = new RoutinePersonalizationResult(
                "테스트 루틴", "테스트", 80, List.of(), "보습", "없음", "요약", List.of(),
                List.of(aiStep(1, SafetyLevel.SAFE), aiStep(2, SafetyLevel.SAFE)), List.of());
        VideoRoutineExtraction extraction = new VideoRoutineExtraction(
                "cache", "video", context.youtubeUrl(), "gemini-test", "1.0", "{}", 10, 5);

        AssembledResult assembled = assembler.assemble(
                context,
                List.of(estimated, highRisk),
                new Response(result, "gpt-test", 20, 10),
                extraction,
                new ShortformProductEnrichmentService.BatchResult(
                        java.util.Map.of(1, estimatedEnrichment, 2, highRiskEnrichment),
                        "gpt-test+gemini-test", "2.0+gemini-1.0", 40, 20, 0, 2));

        assertThat(assembled.analysis().steps().get(0).safetyLevel()).isEqualTo(SafetyLevel.UNKNOWN);
        assertThat(assembled.analysis().steps().get(0).reasons().getFirst().assessmentCategory())
                .isEqualTo(AssessmentCategory.CAUTION);
        assertThat(assembled.analysis().steps().get(1).safetyLevel()).isEqualTo(SafetyLevel.CAUTION);
    }

    private ProductEnrichmentData enrichment(
            IngredientVerificationStatus status,
            ProductEnrichmentResult.Ingredient ingredient
    ) {
        return new ProductEnrichmentData(
                "테스트",
                "테스트 크림",
                "테스트 처방",
                status == IngredientVerificationStatus.ESTIMATED ? 0.72 : 0.95,
                status,
                List.of(new ProductEnrichmentResult.Source(
                        "https://example.com/product", "product", IngredientSourceType.OFFICIAL)),
                List.of(ingredient));
    }

    private Step step(int order, IdentificationLevel level, String productName) {
        return new Step(
                order,
                "00:0" + order,
                null,
                "얼굴",
                "도포",
                "흡수",
                "보습",
                PurposeBasis.DIRECTLY_STATED,
                null,
                level,
                "토너",
                productName == null ? null : "라운드랩",
                productName,
                null,
                productName,
                null,
                List.of(EvidenceSource.ON_SCREEN_TEXT),
                "영상 자막에서 확인",
                level == IdentificationLevel.EXACT_PRODUCT ? 0.95 : 0.7
        );
    }

    private RoutinePersonalizationResult.StepAnalysis aiStep(int order, SafetyLevel level) {
        return new RoutinePersonalizationResult.StepAnalysis(
                order,
                85,
                "피부 타입에 잘 맞습니다.",
                level,
                "AI 안전도 참고",
                "사용 전 패치 테스트를 권장합니다.",
                List.of(new RoutinePersonalizationResult.Reason(
                        AssessmentCategory.BENEFICIAL, "보습", "보습 목적의 단계입니다.", "AI_ESTIMATED"))
        );
    }
}
