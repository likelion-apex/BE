package feat.apex_BE.product.controller;

import feat.apex_BE.global.common.ApiResponse;
import feat.apex_BE.product.dto.response.ProductSearchResponse;
import feat.apex_BE.product.service.ProductSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Product", description = "화장품 상품 검색 API")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductSearchService productSearchService;

    @Operation(
            summary = "화장품 상품 검색",
            description = """
                    화장품 이름(예: "일리윤 로션")으로 네이버 쇼핑에서 가장 연관도 높은 상품 1건을 검색하여
                    상품명, 이미지, 가격과 함께 상세페이지에서 OCR로 추출한 성분 정보를 반환합니다.
                    성분은 상세페이지 구조에 따라 추출되지 않을 수 있으며, 이 경우 null로 응답됩니다.
                    """
    )
    @GetMapping("/search")
    public ApiResponse<ProductSearchResponse> search(
            @Parameter(description = "검색할 화장품 이름", example = "일리윤 로션")
            @RequestParam String query
    ) {
        return ApiResponse.success(productSearchService.search(query));
    }
}
