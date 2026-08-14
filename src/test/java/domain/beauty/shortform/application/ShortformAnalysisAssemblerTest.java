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
import domain.beauty.shortform.client.RoutinePersonalizationResult.Response;
import domain.beauty.shortform.config.OpenAiRoutineProperties;
import domain.beauty.shortform.domain.OptimizationStatus;
import domain.beauty.shortform.domain.SafetyLevel;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ReasonTone;
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
        MatchedVideoStep exact = new MatchedVideoStep(step(1, IdentificationLevel.EXACT_PRODUCT, "독도 토너"), 10L, null);
        MatchedVideoStep categoryOnly = new MatchedVideoStep(step(2, IdentificationLevel.CATEGORY_ONLY, null), null, null);

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
                context, List.of(exact, categoryOnly), new Response(result, "gpt-test", 20, 10), extraction);

        assertThat(assembled.analysis().steps().get(0).safetyLevel()).isEqualTo(SafetyLevel.SAFE);
        assertThat(assembled.analysis().steps().get(1).safetyLevel()).isEqualTo(SafetyLevel.UNKNOWN);
        assertThat(assembled.analysis().steps().get(1).ingredients()).isEmpty();
        assertThat(assembled.optimization().steps().get(0).status()).isEqualTo(OptimizationStatus.REPLACED);
        assertThat(assembled.optimization().steps().get(1).status()).isEqualTo(OptimizationStatus.NO_INVENTORY_MATCH);
        assertThat(assembled.optimization().replacedCount()).isEqualTo(1);
        assertThat(assembled.optimization().missingCount()).isEqualTo(1);
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
                        ReasonTone.POSITIVE, "보습", "보습 목적의 단계입니다.", "AI_ESTIMATED")),
                20,
                List.of(new RoutinePersonalizationResult.Ingredient("판테놀", List.of("피부 보습")))
        );
    }
}
