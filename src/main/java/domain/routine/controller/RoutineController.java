package domain.routine.controller;

import domain.routine.dto.request.RoutineStepCompletionRequest;
import domain.routine.dto.response.ArchivedRoutineListResponse;
import domain.routine.dto.response.DailyRoutineResponse;
import domain.routine.dto.response.RoutineDetailResponse;
import domain.routine.service.RoutineService;
import global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Routine", description = "루틴 관련 API")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RoutineController {

    private final RoutineService routineService;

    @Operation(
            summary = "오늘의 데일리 루틴 조회",
            description = "현재 시각 기준 DAY/NIGHT로 판별한 오늘의 활성 루틴과 각 단계의 완료 여부, 달성률을 반환합니다. "
                    + "활성 루틴이 없으면 data가 null입니다."
    )
    @GetMapping("/api/v1/routines/daily")
    public ApiResponse<DailyRoutineResponse> getDailyRoutine(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(routineService.getDailyRoutine(memberId));
    }

    @Operation(summary = "데일리 루틴 스텝 완료 토글")
    @PatchMapping("/api/v1/routine-logs/today/steps/{stepId}")
    public ApiResponse<DailyRoutineResponse> updateStepCompletion(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long stepId,
            @Valid @RequestBody RoutineStepCompletionRequest request) {
        return ApiResponse.success(routineService.updateStepCompletion(memberId, stepId, request.completed()));
    }

    @Operation(summary = "데일리 루틴 전체 완료 처리")
    @PostMapping("/api/v1/routine-logs/today/complete")
    public ApiResponse<DailyRoutineResponse> completeToday(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(routineService.completeToday(memberId));
    }

    @Operation(summary = "데일리 루틴 스텝 전체완료 일괄처리")
    @PostMapping("/api/v1/routine-logs/today/steps/complete-all")
    public ApiResponse<DailyRoutineResponse> completeAllSteps(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(routineService.completeAllSteps(memberId));
    }

    @Operation(
            summary = "루틴 기록 조회 (캘린더 월별 / 특정 날짜)",
            description = "date가 있으면 그 날짜의 상세 기록을, 없으면 year/month 기준 캘린더 월별 요약을 반환합니다."
    )
    @GetMapping("/api/v1/routines/logs")
    public ApiResponse<?> getRoutineLogs(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) LocalDate date) {
        if (date != null) {
            return ApiResponse.success(routineService.getDailyLogDetail(memberId, date));
        }
        return ApiResponse.success(routineService.getCalendarMonth(memberId, year, month));
    }

    @Operation(summary = "루틴 보관함 목록 조회")
    @GetMapping("/api/v1/routines")
    public ApiResponse<ArchivedRoutineListResponse> getArchivedRoutines(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "3M") String period,
            @RequestParam(defaultValue = "LATEST") String sort) {
        return ApiResponse.success(routineService.getArchivedRoutines(memberId, period, sort));
    }

    @Operation(summary = "보관함 루틴 상세 조회")
    @GetMapping("/api/v1/routines/{routineId}")
    public ApiResponse<RoutineDetailResponse> getRoutineDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long routineId) {
        return ApiResponse.success(routineService.getRoutineDetail(memberId, routineId));
    }
}