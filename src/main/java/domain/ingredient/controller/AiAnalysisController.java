package domain.ingredient.controller;

import domain.ingredient.dto.response.AiRoutineAnalysisResponse;
import domain.ingredient.service.AiAnalysisService;
import global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Ingredient", description = "성분 기반 분석 관련 API")
@RestController
@RequestMapping("/api/v1/ai-analysis")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    @Operation(
            summary = "AI 루틴 분석",
            description = "제품명만으로 회원의 피부타입/피부고민을 반영한 AI 종합 등급(SAFE/MEH/GOOD/RISK)과 근거 코멘트를 반환합니다. "
                    + "인벤토리에 등록되어 있지 않은 제품도 조회할 수 있습니다."
    )
    @GetMapping
    public ApiResponse<AiRoutineAnalysisResponse> analyze(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "제품명", example = "이니스프리 판테놀 세럼") @RequestParam String productName) {
        return ApiResponse.success(aiAnalysisService.analyze(memberId, productName));
    }
}
