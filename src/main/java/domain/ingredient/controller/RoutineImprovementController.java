package domain.ingredient.controller;

import domain.ingredient.dto.response.RoutineImprovementResponse;
import domain.ingredient.service.RoutineImprovementService;
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
@RequestMapping("/api/v1/routine-improvements")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RoutineImprovementController {

    private final RoutineImprovementService routineImprovementService;

    @Operation(
            summary = "내 루틴 개선 방향 제시",
            description = "로그인한 사용자의 보유 인벤토리 전체를 기준으로, 쿼리 파라미터로 전달된 제품명(및 성분)과 "
                    + "보유 제품들 간의 시너지·충돌·중복(대체) 관계를 분석하여 반환합니다."
    )
    @GetMapping
    public ApiResponse<RoutineImprovementResponse> analyze(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "제품명", example = "AHA 필링 세럼") @RequestParam String productName) {
        return ApiResponse.success(routineImprovementService.analyze(memberId, productName));
    }
}
