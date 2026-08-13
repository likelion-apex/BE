package domain.inventory.controller;

import domain.inventory.dto.request.FavoriteUpdateRequest;
import domain.inventory.dto.request.InventoryCreateRequest;
import domain.inventory.dto.response.AiAnalysisResponse;
import domain.inventory.dto.response.FavoriteInventoryResponse;
import domain.inventory.dto.response.FavoriteUpdateResponse;
import domain.inventory.dto.response.IngredientAnalysisResponse;
import domain.inventory.dto.response.InventoryCreateResponse;
import domain.inventory.dto.response.InventoryDeleteResponse;
import domain.inventory.dto.response.InventoryListResponse;
import domain.inventory.service.InventoryService;
import global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * #3(화장품 제거)과 #7(인벤토리 삭제)은 명세서상 동일한 API(DELETE /api/v1/inventory/{inventoryId})이므로
 * remove() 메서드 하나로 통합되어 있다.
 */
@Tag(name = "Inventory", description = "사용자 인벤토리(보유 MY / 위시 WISH) 관련 API")
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "즐겨찾는 화장품 노출", description = "홈 화면 카드용으로 즐겨찾기(isFavorite: true)된 인벤토리 항목을 반환합니다.")
    @GetMapping("/favorites")
    public ApiResponse<FavoriteInventoryResponse> getFavorites(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "조회 개수 (기본값 4)") @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(inventoryService.getFavorites(memberId, limit));
    }

    @Operation(summary = "인벤토리 전체 조회", description = "필터링 없이 사용자 인벤토리에 등록된 화장품 전체를 조회합니다.")
    @GetMapping
    public ApiResponse<InventoryListResponse> getAll(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(inventoryService.getAll(memberId));
    }

    @Operation(
            summary = "인벤토리 추가",
            description = "화장품을 인벤토리에 추가합니다. productId(검색으로 조회한 기존 상품) 또는 productName(마스터 DB에 없는 신규 "
                    + "상품)을 하나 이상 보내야 하며, 신규 상품은 등록 시 이미지 검색과 카테고리 AI 자동 분류가 함께 수행됩니다."
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<InventoryCreateResponse> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody InventoryCreateRequest request) {
        return ApiResponse.success(inventoryService.create(memberId, request));
    }

    @Operation(summary = "인벤토리 삭제", description = "사용자 인벤토리에서 특정 화장품을 삭제합니다.")
    @DeleteMapping("/{inventoryId}")
    public ApiResponse<InventoryDeleteResponse> remove(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "인벤토리 ID") @PathVariable Long inventoryId) {
        return ApiResponse.success(inventoryService.remove(memberId, inventoryId));
    }

    @Operation(summary = "즐겨찾기 등록/해제", description = "특정 인벤토리 항목의 즐겨찾기 상태를 등록(true) 또는 해제(false)합니다.")
    @PatchMapping("/{inventoryId}/favorite")
    public ApiResponse<FavoriteUpdateResponse> updateFavorite(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "인벤토리 ID") @PathVariable Long inventoryId,
            @Valid @RequestBody FavoriteUpdateRequest request) {
        return ApiResponse.success(inventoryService.updateFavorite(memberId, inventoryId, request.isFavorite()));
    }

    @Operation(
            summary = "AI 맞춤 분석",
            description = "회원의 피부타입/피부고민과 화장품 전성분을 바탕으로 AI가 산출한 종합 점수(0~100)와 판단 근거 키워드 3가지를 반환합니다."
    )
    @GetMapping("/{inventoryId}/ai-analysis")
    public ApiResponse<AiAnalysisResponse> getAiAnalysis(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "인벤토리 ID") @PathVariable Long inventoryId) {
        return ApiResponse.success(inventoryService.getAiAnalysis(memberId, inventoryId));
    }

    @Operation(
            summary = "성분 분석",
            description = "화장품의 전성분 목록과 각 성분별 배합목적을 반환합니다. (feature/searchapi의 성분 검색 API를 참고하여 배합목적을 추가함)"
    )
    @GetMapping("/{inventoryId}/ingredients")
    public ApiResponse<IngredientAnalysisResponse> getIngredientAnalysis(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "인벤토리 ID") @PathVariable Long inventoryId) {
        return ApiResponse.success(inventoryService.getIngredientAnalysis(memberId, inventoryId));
    }
}
