package domain.beauty.shortform.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import domain.beauty.shortform.domain.AssessmentCategory;
import domain.beauty.shortform.domain.IngredientDataStatus;
import domain.beauty.shortform.domain.OptimizationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiStructuredResultValidatorTest {

    @Test
    void rejectsRoutineIngredientThatWasNotIncludedInInput() {
        RoutinePersonalizationInput input = routineInput();
        RoutinePersonalizationResult invalid = routineResult(
                List.of("히알루론산"), 1L, List.of("판테놀"), new RoutinePersonalizationResult.ScoreBreakdown(30, 25));

        assertThatThrownBy(() -> GeminiStructuredResultValidator.validateRoutine(input, invalid))
                .isInstanceOf(GeminiCandidateRejectedException.class)
                .hasMessageContaining("입력에 없는 성분명");
    }

    @Test
    void rejectsUnknownInventoryIdAndOutOfRangeScore() {
        RoutinePersonalizationInput input = routineInput();
        RoutinePersonalizationResult unknownInventory = routineResult(
                List.of("판테놀"), 999L, List.of("판테놀"), new RoutinePersonalizationResult.ScoreBreakdown(30, 25));
        RoutinePersonalizationResult invalidScore = routineResult(
                List.of("판테놀"), 1L, List.of("판테놀"), new RoutinePersonalizationResult.ScoreBreakdown(41, 25));

        assertThatThrownBy(() -> GeminiStructuredResultValidator.validateRoutine(input, unknownInventory))
                .isInstanceOf(GeminiCandidateRejectedException.class)
                .hasMessageContaining("입력에 없거나 카테고리가 다른");
        assertThatThrownBy(() -> GeminiStructuredResultValidator.validateRoutine(input, invalidScore))
                .isInstanceOf(GeminiCandidateRejectedException.class)
                .hasMessageContaining("점수 범위");
    }

    @Test
    void rejectsOptimizationIngredientThatDoesNotBelongToSelectedProduct() {
        OptimizationReasonInput input = new OptimizationReasonInput(
                new OptimizationReasonInput.MemberProfile("민감성", List.of("피부 진정")),
                List.of(new OptimizationReasonInput.Step(
                        1,
                        OptimizationStatus.REPLACED,
                        optimizationProduct("영상 앰플", "히알루론산"),
                        optimizationProduct("보유 앰플", "판테놀"))));
        OptimizationReasonResult invalid = new OptimizationReasonResult(List.of(
                new OptimizationReasonResult.StepReason(
                        1,
                        "보유 앰플로 진정 단계를 구성합니다.",
                        new RoutinePersonalizationResult.ScoreBreakdown(30, 25),
                        List.of("히알루론산"))));

        assertThatThrownBy(() -> GeminiStructuredResultValidator.validateOptimization(input, invalid))
                .isInstanceOf(GeminiCandidateRejectedException.class)
                .hasMessageContaining("입력에 없는 성분명");
    }

    private RoutinePersonalizationInput routineInput() {
        RoutinePersonalizationInput.Ingredient panthenol = new RoutinePersonalizationInput.Ingredient(
                1, "판테놀", List.of("진정"), List.of("피부 진정"), 1, false, false);
        return new RoutinePersonalizationInput(
                new RoutinePersonalizationInput.MemberProfile("민감성", List.of("피부 진정")),
                new RoutinePersonalizationInput.VideoContext("video", "진정 루틴", List.of()),
                List.of(new RoutinePersonalizationInput.VideoStep(
                        1, "앰플", "ESSENCE_SERUM", "브랜드", "영상 앰플", "브랜드", "영상 앰플",
                        "피부 진정", "영상에서 확인", 1.0, null,
                        IngredientDataStatus.AVAILABLE, List.of(panthenol))),
                List.of(new RoutinePersonalizationInput.InventoryProduct(
                        1L, 10L, "ESSENCE_SERUM", "브랜드", "보유 앰플",
                        IngredientDataStatus.AVAILABLE, List.of(panthenol))));
    }

    private RoutinePersonalizationResult routineResult(
            List<String> videoIngredients,
            Long inventoryId,
            List<String> inventoryIngredients,
            RoutinePersonalizationResult.ScoreBreakdown inventoryScore
    ) {
        return new RoutinePersonalizationResult(
                "민감 피부 진정 루틴",
                "진정",
                List.of("피부 진정"),
                "피부 진정",
                "앰플 보습",
                "확인된 앰플로 피부를 진정하는 루틴입니다.",
                List.of(),
                List.of(new RoutinePersonalizationResult.StepAnalysis(
                        1,
                        new RoutinePersonalizationResult.ScoreBreakdown(30, 25),
                        List.of("피부 진정"),
                        List.of(
                                new RoutinePersonalizationResult.Reason(
                                        AssessmentCategory.SAFE, "안전성", "확인된 사용 목적입니다.", "영상"),
                                new RoutinePersonalizationResult.Reason(
                                        AssessmentCategory.BENEFICIAL, "진정", "피부 진정에 도움을 줍니다.", "성분")),
                        videoIngredients)),
                List.of(new RoutinePersonalizationResult.InventoryRecommendation(
                        1, inventoryId, "보유 제품의 진정 성분을 활용합니다.", inventoryScore, inventoryIngredients)));
    }

    private OptimizationReasonInput.Product optimizationProduct(String name, String ingredient) {
        return new OptimizationReasonInput.Product(
                "ESSENCE_SERUM",
                name,
                "피부 진정",
                List.of("피부 진정"),
                IngredientDataStatus.AVAILABLE,
                List.of(new OptimizationReasonInput.Ingredient(
                        ingredient, List.of("진정"), List.of("피부 진정"))));
    }
}
