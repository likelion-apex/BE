package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import domain.beauty.shortform.application.ShortformAnalysisStateService.AnalysisProfile;
import domain.beauty.shortform.client.OpenAiOptimizationReasonClient;
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
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OptimizationReasonRefresherTest {

    private final InventoryProductEvidenceService evidenceService = mock(InventoryProductEvidenceService.class);
    private final OpenAiOptimizationReasonClient reasonClient = mock(OpenAiOptimizationReasonClient.class);
    private final OptimizationReasonRefresher refresher = new OptimizationReasonRefresher(
            evidenceService,
            reasonClient,
            new OptimizationReasonComposer(),
            new OptimizationScoreCalculator());

    @Test
    void savesGroundedFallbackWhenAiRefreshFails() {
        when(evidenceService.enrich(any())).thenReturn(Map.of());
        when(reasonClient.generate(any())).thenThrow(
                new CustomException(ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE));
        ShortformAnalysisSnapshot analysis = new ShortformAnalysisSnapshot(
                "3.0", "video", "https://youtube.com/watch?v=video", "보습 루틴", "맞춤",
                80, List.of("수분 공급"), "보습", "수분", "요약", List.of(), "안내",
                List.of(step()), null);
        RoutineOptimizationSnapshot optimization = new RoutineOptimizationSnapshot(
                1, 0, 1, "확인했습니다.",
                List.of(new OptimizedStep(
                        1, 1, OptimizationStatus.VIDEO_PRODUCT, null, 10L, "ESSENCE_SERUM",
                        "영상 수분 앰플", null, "영상 브랜드", "/video.png",
                        "인벤토리에서 같은 카테고리의 대체 제품을 찾지 못했습니다.")));

        RoutineOptimizationSnapshot refreshed = refresher.refresh(
                new AnalysisProfile(1L, "수부지", List.of("속건조"), List.of()),
                analysis,
                optimization);

        assertThat(refreshed.steps()).singleElement().satisfies(item -> assertThat(item.reason())
                .contains("영상 수분 앰플", "수분 공급", "속건조", "확인된 보유 제품")
                .doesNotContain("같은 카테고리", "님"));
        assertThat(refreshed.overallScore()).isEqualTo(80);
        assertThat(refreshed.highlights()).containsExactly(
                "수부지 맞춤 성분 0개 매칭",
                "알레르기 유발 성분 0개");
        verify(reasonClient).generate(any());
    }

    private StepResult step() {
        return new StepResult(
                1, 1, "00:01", null, "앰플", "영상 브랜드", "영상 수분 앰플",
                "영상 브랜드", "영상 수분 앰플", ProductResolutionStatus.CATALOG_MATCH, 1,
                "/video.png", 10L, 1, "영상에서 제품명을 확인", 80, "수분 공급",
                List.of("수분 공급"), new ScoreBreakdown(30, 25, 25), SafetyLevel.SAFE,
                AssessmentCategory.BENEFICIAL, "피부 안전도", "안전합니다.", List.of(),
                IngredientDataStatus.UNAVAILABLE, IngredientVerificationStatus.UNVERIFIED,
                null, List.of(), null, null, List.of());
    }
}
