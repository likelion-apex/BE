package domain.home.controller;

import domain.home.dto.request.DailyConditionRequest;
import domain.home.dto.response.HomeSummaryResponse;
import domain.home.dto.response.TodayConditionResponse;
import domain.home.service.HomeService;
import global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Home", description = "홈 화면 요약 API")
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class HomeController {

    private final HomeService homeService;

    @Operation(
            summary = "홈 요약 조회",
            description = "오늘의 컨디션 체크 여부, 오늘의 활성 루틴(현재 시각 기준 DAY/NIGHT 자동 판별, 없으면 null), "
                    + "즐겨찾는 화장품 목록(최대 4개)을 반환합니다."
    )
    @GetMapping
    public ApiResponse<HomeSummaryResponse> getSummary(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(homeService.getSummary(memberId));
    }

    @Operation(summary = "오늘의 컨디션 기록/취소")
    @PostMapping("/condition")
    public ApiResponse<TodayConditionResponse> updateCondition(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody DailyConditionRequest request) {
        return ApiResponse.success(homeService.updateCondition(memberId, request));
    }
}