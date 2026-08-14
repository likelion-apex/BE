package domain.beauty.shortform.api;

import domain.beauty.shortform.api.ShortformAnalysisResponses.Applied;
import domain.beauty.shortform.api.ShortformAnalysisResponses.Created;
import domain.beauty.shortform.api.ShortformAnalysisResponses.Detail;
import domain.beauty.shortform.api.ShortformAnalysisResponses.History;
import domain.beauty.shortform.api.ShortformAnalysisResponses.Optimization;
import domain.beauty.shortform.api.ShortformAnalysisResponses.ProductDetail;
import domain.beauty.shortform.api.ShortformAnalysisResponses.Status;
import domain.beauty.shortform.application.ShortformAnalysisService;
import global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shortform-analyses")
@Tag(name = "AI Routine Analysis", description = "YouTube 전체 스킨케어 루틴 분석 API")
@SecurityRequirement(name = "bearerAuth")
public class ShortformAnalysisController {

    private final ShortformAnalysisService service;

    public ShortformAnalysisController(ShortformAnalysisService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "전체 스킨케어 루틴 분석 요청")
    public ResponseEntity<ApiResponse<Created>> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody CreateShortformAnalysisRequest request
    ) {
        Created result = service.create(memberId, request.videoUrl());
        HttpStatus status = result.reused() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiResponse.success("루틴 분석을 시작했습니다.", result));
    }

    @GetMapping
    @Operation(summary = "최근 분석한 루틴 조회")
    public ApiResponse<History> recent(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(service.recent(memberId));
    }

    @GetMapping("/{analysisId}/status")
    @Operation(summary = "루틴 분석 진행 상태 조회")
    public ApiResponse<Status> status(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long analysisId
    ) {
        return ApiResponse.success(service.status(memberId, analysisId));
    }

    @PostMapping("/{analysisId}/cancel")
    @Operation(summary = "루틴 분석 취소")
    public ApiResponse<Status> cancel(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long analysisId
    ) {
        return ApiResponse.success("루틴 분석이 취소되었습니다.", service.cancel(memberId, analysisId));
    }

    @GetMapping("/{analysisId}")
    @Operation(summary = "전체 스킨케어 루틴 분석 결과 조회")
    public ApiResponse<Detail> detail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long analysisId
    ) {
        return ApiResponse.success(service.detail(memberId, analysisId));
    }

    @GetMapping("/{analysisId}/results/{resultId}")
    @Operation(summary = "루틴 단계별 제품 분석 상세 조회")
    public ApiResponse<ProductDetail> productDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long analysisId,
            @PathVariable Long resultId
    ) {
        return ApiResponse.success(service.productDetail(memberId, analysisId, resultId));
    }

    @PostMapping("/{analysisId}/optimize")
    @Operation(summary = "내 인벤토리 기반 루틴 최적화")
    public ApiResponse<Optimization> optimize(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long analysisId
    ) {
        return ApiResponse.success("인벤토리 맞춤 루틴을 구성했습니다.", service.optimize(memberId, analysisId));
    }

    @PostMapping("/{analysisId}/apply")
    @Operation(summary = "분석한 루틴 적용 또는 보관")
    public ApiResponse<Applied> apply(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long analysisId,
            @Valid @RequestBody ApplyShortformRoutineRequest request
    ) {
        return ApiResponse.success(
                "루틴을 저장했습니다.",
                service.apply(memberId, analysisId, request.saveType())
        );
    }
}
