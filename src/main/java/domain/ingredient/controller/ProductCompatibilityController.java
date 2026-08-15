package domain.ingredient.controller;

import domain.ingredient.dto.request.ProductCompatibilityRequest;
import domain.ingredient.dto.response.ProductCompatibilityResponse;
import domain.ingredient.service.ProductCompatibilityService;
import global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Ingredient", description = "성분 기반 분석 관련 API")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ProductCompatibilityController {

    private final ProductCompatibilityService productCompatibilityService;

    @Operation(
            summary = "제품 간 궁합 비교",
            description = "기준 제품(baseProductId) 1개를 비교 대상 제품 목록(compareProductIds)과 1:N으로 비교합니다. "
                    + "성분 간 CONFLICT/SYNERGY가 등록되어 있으면 그것으로 확정하고, 없으면 카테고리가 같고 공통 성분이 있을 때 "
                    + "SUBSTITUTE(호환)로, 그 외에는 NEUTRAL로 판정합니다. compareProductIds 중 존재하지 않는 ID는 조용히 제외합니다."
    )
    @PostMapping("/compatibility")
    public ApiResponse<ProductCompatibilityResponse> compare(
            @Valid @RequestBody ProductCompatibilityRequest request) {
        return ApiResponse.success(productCompatibilityService.compare(request));
    }
}