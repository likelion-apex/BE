package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import domain.beauty.domain.BeautyRoutineAnalysis;
import domain.beauty.domain.BeautyRoutineAnalysis.EvidenceSource;
import domain.beauty.domain.BeautyRoutineAnalysis.IdentificationLevel;
import domain.beauty.domain.BeautyRoutineAnalysis.PurposeBasis;
import domain.beauty.domain.BeautyRoutineAnalysis.Step;
import domain.beauty.shortform.application.ShortformAnalysisAssembler.AssembledResult;
import domain.beauty.shortform.application.ShortformAnalysisStateService.InventoryFact;
import domain.beauty.shortform.application.ShortformAnalysisStateService.JobContext;
import domain.beauty.shortform.client.RoutinePersonalizationResult;
import domain.beauty.shortform.client.ProductEnrichmentResult;
import domain.beauty.shortform.client.RoutinePersonalizationInput;
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
import domain.inventory.ProductCategory;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ShortformAnalysisAssemblerTest {

    private final RegulationInfoCache regulationInfoCache =
            new RegulationInfoCache(mock(CsmtcsReglMaterialClient.class));
    private final ShortformAnalysisAssembler assembler =
            new ShortformAnalysisAssembler(
                    regulationInfoCache, new OpenAiRoutineProperties(), new ShortformProductCategoryResolver());

    @Test
    void forcesUnknownForCategoryOnlyStepAndIgnoresUnknownInventoryId() {
        JobContext context = new JobContext(
                1L,
                "t1S24pgO2XQ",
                "https://www.youtube.com/watch?v=t1S24pgO2XQ",
                "수부지",
                List.of("민감성"),
                List.of(new InventoryFact(100L, 20L, "진정 토너", "테스트", "SKIN_TONER", null))
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
                10L, ProductCategory.SKIN_TONER, null, "라운드랩", "1025 독도 토너", ProductResolutionStatus.CATALOG_MATCH,
                1, IngredientDataStatus.AVAILABLE, exactEnrichment);
        MatchedVideoStep categoryOnly = new MatchedVideoStep(
                step(2, IdentificationLevel.CATEGORY_ONLY, null),
                null, ProductCategory.SKIN_TONER, null, null, "토너", ProductResolutionStatus.UNRESOLVED,
                0, IngredientDataStatus.NOT_ELIGIBLE, ProductEnrichmentData.unresolved());

        RoutinePersonalizationResult result = new RoutinePersonalizationResult(
                "수부지 진정 루틴",
                "민감 피부 맞춤",
                List.of("보습 단계가 균형적입니다."),
                "장벽 보호",
                "판테놀 + 히알루론산",
                "단계별 사용을 권장합니다.",
                List.of(),
                List.of(
                        aiStep(1, 30, 25, AssessmentCategory.BENEFICIAL),
                        aiStep(2, 20, 18, AssessmentCategory.CAUTION)),
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

        assertThat(assembled.analysis().steps().get(0).matchScore()).isEqualTo(80);
        assertThat(assembled.analysis().steps().get(1).matchScore()).isEqualTo(50);
        assertThat(assembled.analysis().overallScore()).isEqualTo(65);
        assertThat(assembled.analysis().steps().get(0).scoreBreakdown().ingredientSafety()).isEqualTo(25);
        assertThat(assembled.analysis().steps().get(0).primaryAssessmentCategory())
                .isEqualTo(AssessmentCategory.BENEFICIAL);
        assertThat(assembled.analysis().steps().get(1).safetyLevel()).isEqualTo(SafetyLevel.CAUTION);
        assertThat(assembled.analysis().steps().get(1).ingredients()).isEmpty();
        assertThat(assembled.analysis().steps().get(1).estimatedIngredientCount()).isNull();
        assertThat(assembled.analysis().steps().get(0).ingredientStats().totalCount()).isEqualTo(1);
        assertThat(assembled.optimization().steps().get(0).status()).isEqualTo(OptimizationStatus.REPLACED);
        assertThat(assembled.optimization().steps().get(1).status()).isEqualTo(OptimizationStatus.VIDEO_PRODUCT);
        assertThat(assembled.optimization().replacedCount()).isEqualTo(1);
        assertThat(assembled.optimization().missingCount()).isEqualTo(1);
    }

    @Test
    void rejectsInventoryRecommendationFromDifferentProductCategory() {
        JobContext context = new JobContext(
                1L, "video", "https://www.youtube.com/watch?v=video", "수부지",
                List.of(), List.of(new InventoryFact(
                        100L, 20L, "진정 토너", "테스트", "SKIN_TONER", "/toner.png")));
        ProductEnrichmentData product = enrichment(
                IngredientVerificationStatus.OFFICIAL,
                new ProductEnrichmentResult.Ingredient(
                        1, "히알루론산", List.of("보습제"), List.of("수분 공급"), 1, false, false));
        MatchedVideoStep ampoule = new MatchedVideoStep(
                step(1, IdentificationLevel.EXACT_PRODUCT, "수분 앰플"),
                10L, ProductCategory.SERUM, "/ampoule.png", "테스트", "수분 앰플",
                ProductResolutionStatus.CATALOG_MATCH, 1, IngredientDataStatus.AVAILABLE, product);

        AssembledResult assembled = assembleOneStep(
                context, ampoule, resultWithRecommendation(100L, "보습 역할이 비슷합니다."));

        assertThat(assembled.optimization().steps()).singleElement().satisfies(step -> {
            assertThat(step.status()).isEqualTo(OptimizationStatus.VIDEO_PRODUCT);
            assertThat(step.productName()).isEqualTo("수분 앰플");
            assertThat(step.replaceName()).isNull();
            assertThat(step.inventoryId()).isNull();
        });
        assertThat(assembled.optimization().replacedCount()).isZero();
        assertThat(assembled.optimization().missingCount()).isEqualTo(1);
    }

    @Test
    void combinesSameCategoryAndSameProductRecommendationIntoReplacement() throws Exception {
        JobContext context = new JobContext(
                1L, "video", "https://www.youtube.com/watch?v=video", "수부지",
                List.of(), List.of(new InventoryFact(
                        100L, 10L, "보유 수분 앰플", "테스트", "SERUM", "/owned.png")));
        ProductEnrichmentData product = enrichment(
                IngredientVerificationStatus.OFFICIAL,
                new ProductEnrichmentResult.Ingredient(
                        1, "히알루론산", List.of("보습제"), List.of("수분 공급"), 1, false, false));
        MatchedVideoStep ampoule = new MatchedVideoStep(
                step(1, IdentificationLevel.EXACT_PRODUCT, "영상 수분 앰플"),
                10L, ProductCategory.SERUM, "/video.png", "영상 브랜드", "영상 수분 앰플",
                ProductResolutionStatus.CATALOG_MATCH, 1, IngredientDataStatus.AVAILABLE, product);

        AssembledResult assembled = assembleOneStep(
                context, ampoule, resultWithRecommendation(100L, "같은 세럼 카테고리의 보유 제품입니다."));

        assertThat(assembled.optimization().steps()).singleElement().satisfies(step -> {
            assertThat(step.status()).isEqualTo(OptimizationStatus.REPLACED);
            assertThat(step.productName()).isEqualTo("보유 수분 앰플");
            assertThat(step.replaceName()).isEqualTo("영상 수분 앰플");
            assertThat(step.inventoryId()).isEqualTo(100L);
        });
        assertThat(assembled.optimization().replacedCount()).isEqualTo(1);
        String json = new ObjectMapper().writeValueAsString(assembled.optimization());
        assertThat(json).doesNotContain("compatibleCount", "COMPATIBLE");
    }

    @Test
    void neverReplacesUnclassifiedEtcVideoProduct() {
        JobContext context = new JobContext(
                1L, "video", "https://www.youtube.com/watch?v=video", "수부지",
                List.of(), List.of(new InventoryFact(
                        100L, 20L, "보유 기타 제품", "테스트", "ETC", null)));
        MatchedVideoStep unknown = new MatchedVideoStep(
                step(1, IdentificationLevel.CATEGORY_ONLY, null),
                null, ProductCategory.ETC, null, null, "확인되지 않은 제품",
                ProductResolutionStatus.UNRESOLVED, 0, IngredientDataStatus.NOT_ELIGIBLE,
                ProductEnrichmentData.unresolved());

        AssembledResult assembled = assembleOneStep(
                context, unknown, resultWithRecommendation(100L, "기타 제품 추천"));

        assertThat(assembled.optimization().steps()).singleElement().satisfies(step -> {
            assertThat(step.status()).isEqualTo(OptimizationStatus.VIDEO_PRODUCT);
            assertThat(step.replaceName()).isNull();
        });
    }

    @Test
    void usesNormalReasonCardsForEstimatedProductsAndGuardsHighRiskIngredients() {
        JobContext context = new JobContext(
                1L, "video", "https://www.youtube.com/watch?v=video", "건성",
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
                null, ProductCategory.SKIN_TONER, null, "추정 브랜드", "추정 수딩 크림", ProductResolutionStatus.AI_NORMALIZED,
                0.72, IngredientDataStatus.AVAILABLE, estimatedEnrichment);
        MatchedVideoStep highRisk = new MatchedVideoStep(
                step(2, IdentificationLevel.EXACT_PRODUCT, "테스트 크림"),
                null, ProductCategory.SKIN_TONER, null, "테스트", "테스트 크림", ProductResolutionStatus.AI_NORMALIZED,
                0.95, IngredientDataStatus.AVAILABLE, highRiskEnrichment);
        RoutinePersonalizationResult result = new RoutinePersonalizationResult(
                "테스트 루틴", "테스트", List.of(), "보습", "없음", "요약", List.of(),
                List.of(
                        aiStep(1, 30, 25, AssessmentCategory.BENEFICIAL),
                        aiStep(2, 30, 25, AssessmentCategory.SAFE)),
                List.of());
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

        assertThat(assembled.analysis().steps().get(0).primaryAssessmentCategory())
                .isEqualTo(AssessmentCategory.BENEFICIAL);
        assertThat(assembled.analysis().steps().get(0).reasons()).hasSizeBetween(2, 3);
        assertThat(assembled.analysis().steps().get(0).reasons())
                .noneMatch(reason -> reason.title().contains("추정") || reason.description().contains("추정"));
        assertThat(assembled.analysis().steps().get(1).safetyLevel()).isEqualTo(SafetyLevel.WARNING);
        assertThat(assembled.analysis().steps().get(1).primaryAssessmentCategory())
                .isEqualTo(AssessmentCategory.WARNING);
        assertThat(assembled.analysis().steps().get(1).scoreBreakdown().ingredientSafety()).isEqualTo(5);
    }

    @Test
    void omitsNicknameFromPersonalizationInput() throws Exception {
        JobContext context = new JobContext(
                1L, "video", "https://www.youtube.com/watch?v=video", "수부지",
                List.of("속건조"), List.of());
        ProductEnrichmentData product = enrichment(
                IngredientVerificationStatus.OFFICIAL,
                new ProductEnrichmentResult.Ingredient(
                        1, "판테놀", List.of("보습제"), List.of("장벽 보호"), 1, false, false));
        MatchedVideoStep matched = new MatchedVideoStep(
                step(1, IdentificationLevel.EXACT_PRODUCT, "테스트 크림"),
                10L, ProductCategory.SKIN_TONER, null, "테스트", "테스트 크림", ProductResolutionStatus.CATALOG_MATCH,
                0.95, IngredientDataStatus.AVAILABLE, product);
        BeautyRoutineAnalysis extraction = new BeautyRoutineAnalysis(
                "1.0",
                BeautyRoutineAnalysis.AnalysisStatus.COMPLETE,
                BeautyRoutineAnalysis.RoutineType.SKINCARE,
                "수분 공급과 장벽 보호를 위한 루틴",
                List.of(matched.source()),
                List.of());

        RoutinePersonalizationInput input = assembler.toInput(context, extraction, List.of(matched));
        String json = new ObjectMapper().writeValueAsString(input);

        assertThat(json).doesNotContain("nickname");
        assertThat(json).contains("\"skinType\":\"수부지\"");
        assertThat(json).contains("\"productCategory\":\"SKIN_TONER\"");
    }

    @Test
    void replacesOwnedRoutineCopyWithVideoFocusedTitleAndConcreteSummary() {
        JobContext context = new JobContext(
                1L, "video", "https://www.youtube.com/watch?v=video", "수부지",
                List.of("속건조"), List.of());
        ProductEnrichmentData product = enrichment(
                IngredientVerificationStatus.OFFICIAL,
                new ProductEnrichmentResult.Ingredient(
                        1, "판테놀", List.of("보습제"), List.of("장벽 보호"), 1, false, false));
        MatchedVideoStep matched = new MatchedVideoStep(
                step(1, IdentificationLevel.EXACT_PRODUCT, "테스트 크림"),
                10L, ProductCategory.SKIN_TONER, null, "테스트", "테스트 크림", ProductResolutionStatus.CATALOG_MATCH,
                0.95, IngredientDataStatus.AVAILABLE, product);
        RoutinePersonalizationResult result = new RoutinePersonalizationResult(
                "장선우님의 나이트 스킨케어 루틴 분석",
                "수부지 맞춤",
                List.of("수분 공급"),
                "속건조 해결",
                "판테놀 + 보습 성분",
                "장선우님의 나이트 스킨케어 루틴은 피부 진정에 적합합니다.",
                List.of(),
                List.of(aiStep(1, 30, 25, AssessmentCategory.BENEFICIAL)),
                List.of());

        AssembledResult assembled = assembleOneStep(context, matched, result);

        assertThat(assembled.analysis().title()).isEqualTo("속건조 해결 루틴");
        assertThat(assembled.analysis().title()).doesNotContain("장선우", "님", "분석");
        assertThat(assembled.analysis().summary()).contains("피부 진정", "테스트 크림");
        assertThat(assembled.analysis().summary()).doesNotContain("장선우", "님");
    }

    @Test
    void preservesValidFigmaStyleTitleAndConcreteSummary() {
        JobContext context = new JobContext(
                1L, "video", "https://www.youtube.com/watch?v=video", "수부지",
                List.of("속건조"), List.of());
        ProductEnrichmentData product = enrichment(
                IngredientVerificationStatus.OFFICIAL,
                new ProductEnrichmentResult.Ingredient(
                        1, "판테놀", List.of("보습제"), List.of("장벽 보호"), 1, false, false));
        MatchedVideoStep matched = new MatchedVideoStep(
                step(1, IdentificationLevel.EXACT_PRODUCT, "테스트 크림"),
                10L, ProductCategory.SKIN_TONER, null, "테스트", "테스트 크림", ProductResolutionStatus.CATALOG_MATCH,
                0.95, IngredientDataStatus.AVAILABLE, product);
        String summary = "수분 공급(테스트 크림)과 판테놀 성분을 중심으로 피부 장벽을 관리하는 루틴입니다.";
        RoutinePersonalizationResult result = new RoutinePersonalizationResult(
                "속건조 타파 루틴",
                "수부지 맞춤",
                List.of("수분 공급"),
                "속건조 해결",
                "판테놀 + 보습 성분",
                summary,
                List.of(),
                List.of(aiStep(1, 30, 25, AssessmentCategory.BENEFICIAL)),
                List.of());

        AssembledResult assembled = assembleOneStep(context, matched, result);

        assertThat(assembled.analysis().title()).isEqualTo("속건조 타파 루틴");
        assertThat(assembled.analysis().summary()).isEqualTo(summary);
    }

    @Test
    void fallsBackToKnownCategoryWhenProductAndIngredientsAreUnresolved() {
        JobContext context = new JobContext(
                1L, "video", "https://www.youtube.com/watch?v=video", "민감성",
                List.of(), List.of());
        MatchedVideoStep unresolved = new MatchedVideoStep(
                step(1, IdentificationLevel.CATEGORY_ONLY, null),
                null, ProductCategory.SKIN_TONER, null, null, "토너", ProductResolutionStatus.UNRESOLVED,
                0, IngredientDataStatus.NOT_ELIGIBLE, ProductEnrichmentData.unresolved());
        RoutinePersonalizationResult result = new RoutinePersonalizationResult(
                "피부 진정 루틴",
                "민감 피부 맞춤",
                List.of("피부 진정"),
                "피부 진정",
                "영상 속 제품 조합",
                "피부를 편안하게 정돈하는 루틴입니다.",
                List.of(),
                List.of(aiStep(1, 20, 18, AssessmentCategory.CAUTION)),
                List.of());

        AssembledResult assembled = assembleOneStep(context, unresolved, result);

        assertThat(assembled.analysis().summary()).contains("토너 단계를 포함해");
        assertThat(assembled.analysis().summary()).doesNotContain("null");
    }

    @Test
    void includesKnownCautionIngredientInGeneratedSummary() {
        JobContext context = new JobContext(
                1L, "video", "https://www.youtube.com/watch?v=video", "민감성",
                List.of(), List.of());
        ProductEnrichmentData product = enrichment(
                IngredientVerificationStatus.OFFICIAL,
                new ProductEnrichmentResult.Ingredient(
                        1, "살리실산", List.of("각질 제거"), List.of(), 4, true, false));
        MatchedVideoStep matched = new MatchedVideoStep(
                step(1, IdentificationLevel.EXACT_PRODUCT, "테스트 크림"),
                10L, ProductCategory.SKIN_TONER, null, "테스트", "테스트 크림", ProductResolutionStatus.CATALOG_MATCH,
                0.95, IngredientDataStatus.AVAILABLE, product);
        RoutinePersonalizationResult result = new RoutinePersonalizationResult(
                "각질 케어 루틴",
                "민감 피부 맞춤",
                List.of("각질 케어"),
                "피부결 정돈",
                "각질 케어 + 보습",
                "피부결을 정돈하는 단계로 구성되어 있습니다.",
                List.of(),
                List.of(aiStep(1, 20, 18, AssessmentCategory.CAUTION)),
                List.of());

        AssembledResult assembled = assembleOneStep(context, matched, result);

        assertThat(assembled.analysis().summary())
                .contains("테스트 크림", "살리실산", "사용량과 빈도");
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

    private AssembledResult assembleOneStep(
            JobContext context,
            MatchedVideoStep matched,
            RoutinePersonalizationResult result
    ) {
        VideoRoutineExtraction extraction = new VideoRoutineExtraction(
                "cache", context.videoId(), context.youtubeUrl(), "gemini-test", "1.0", "{}", 10, 5);
        return assembler.assemble(
                context,
                List.of(matched),
                new Response(result, "gpt-test", 20, 10),
                extraction,
                new ShortformProductEnrichmentService.BatchResult(
                        java.util.Map.of(1, matched.enrichment()),
                        "gpt-test", "1.0", 40, 20, 0, 1));
    }

    private RoutinePersonalizationResult.StepAnalysis aiStep(
            int order,
            int skinTypeFit,
            int benefitFit,
            AssessmentCategory category
    ) {
        return new RoutinePersonalizationResult.StepAnalysis(
                order,
                new RoutinePersonalizationResult.ScoreBreakdown(skinTypeFit, benefitFit),
                List.of("피부 진정", "수분 공급"),
                List.of(new RoutinePersonalizationResult.Reason(
                        category, "보습 균형", "피부 컨디션에 필요한 보습을 채워줘요.", "INGREDIENT_PROFILE"))
        );
    }

    private RoutinePersonalizationResult resultWithRecommendation(Long inventoryId, String reason) {
        return new RoutinePersonalizationResult(
                "수분 루틴", "수부지 맞춤", List.of("수분 공급"), "수분 공급", "보습 성분",
                "영상 속 제품으로 수분을 공급하는 루틴입니다.", List.of(),
                List.of(aiStep(1, 30, 25, AssessmentCategory.BENEFICIAL)),
                List.of(new RoutinePersonalizationResult.InventoryRecommendation(1, inventoryId, reason)));
    }
}
