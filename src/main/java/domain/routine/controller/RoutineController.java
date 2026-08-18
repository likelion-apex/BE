package domain.routine.controller;

import domain.routine.domain.RoutineType;
import domain.routine.dto.request.RoutineCreateRequest;
import domain.routine.dto.request.RoutineStepCompletionRequest;
import domain.routine.dto.response.ArchivedRoutineListResponse;
import domain.routine.dto.response.DailyRoutineResponse;
import domain.routine.dto.response.RoutineCreateResponse;
import domain.routine.dto.response.RoutineDeleteResponse;
import domain.routine.dto.response.RoutineDetailResponse;
import domain.routine.dto.response.RoutineGenerationResponse;
import domain.routine.service.RoutineService;
import global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "LATEST") String sort) {
        return ApiResponse.success(routineService.getArchivedRoutines(memberId, year, sort));
    }

    @Operation(summary = "보관함 루틴 상세 조회")
    @GetMapping("/api/v1/routines/{routineId}")
    public ApiResponse<RoutineDetailResponse> getRoutineDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long routineId) {
        return ApiResponse.success(routineService.getRoutineDetail(memberId, routineId));
    }

    @Operation(summary = "보관함 루틴 삭제")
    @DeleteMapping("/api/v1/routines/{routineId}")
    public ApiResponse<RoutineDeleteResponse> deleteRoutine(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long routineId) {
        return ApiResponse.success(routineService.deleteRoutine(memberId, routineId));
    }

    @Operation(
            summary = "보관함 루틴 오늘 적용",
            description = "같은 타입(DAY/NIGHT)의 기존 활성 루틴이 있으면 보관함으로 전환하고, 이 루틴을 오늘의 활성 루틴으로 전환합니다."
    )
    @PostMapping("/api/v1/routines/{routineId}/apply-today")
    public ApiResponse<DailyRoutineResponse> applyToday(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long routineId) {
        return ApiResponse.success(routineService.applyToday(memberId, routineId));
    }

    @Operation(
            summary = "AI 자동생성 루틴 미리보기",
            description = "내 보유(MY) 인벤토리 중 카테고리별 피부적합도(4.4) 최고점 제품을 골라 조합을 제안합니다. "
                    + "아무것도 저장하지 않으며, 조합 내부 성분 충돌(4.3)이 있으면 warnings로만 안내합니다."
    )
    @PostMapping("/api/v1/routines/generate")
    public ApiResponse<RoutineGenerationResponse> generateRoutine(
            @AuthenticationPrincipal Long memberId,
            @RequestParam RoutineType routineType) {
        return ApiResponse.success(routineService.generateRoutine(memberId, routineType));
    }

    @Operation(
            summary = "루틴 생성",
            description = "saveType=TODAY면 오늘의 활성 루틴으로(기존 같은 타입 루틴은 보관함으로 전환), "
                    + "LIBRARY면 보관함에 바로 저장합니다."
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/v1/routines")
    public ApiResponse<RoutineCreateResponse> createRoutine(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody RoutineCreateRequest request) {
        return ApiResponse.success(routineService.createRoutine(memberId, request));
    }
}