package domain.ingredient.controller;

import domain.ingredient.dto.response.SkinAnalysisResponse;
import domain.ingredient.service.SkinAnalysisService;
import global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Ingredient", description = "성분 기반 분석 관련 API")
@RestController
@RequestMapping("/api/v1/products/{productId}")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SkinAnalysisController {

    private final SkinAnalysisService skinAnalysisService;

    @Operation(
            summary = "제품 피부적합도 분석",
            description = "회원의 피부타입/피부고민과 제품 전성분을 바탕으로 AI 종합 점수(matchScore), EWG 등급 분포 기반 안전성 한 줄 평가"
                    + "(safetyEvaluation), AI 근거 키워드와 유의사항(aiAnalysis), 전성분 프로필(ingredientProfile)을 반환합니다. "
                    + "AI 분석이 실패하면 matchScore는 EWG 등급 기반 규칙으로 대체 산출되고 aiAnalysis.reasons는 빈 리스트가 됩니다."
    )
    @GetMapping("/skin-analysis")
    public ApiResponse<SkinAnalysisResponse> analyze(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "제품 ID") @PathVariable Long productId) {
        return ApiResponse.success(skinAnalysisService.analyze(memberId, productId));
    }
}