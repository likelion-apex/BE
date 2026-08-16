package domain.inventory.controller;

import domain.inventory.dto.response.ProductListResponse;
import domain.inventory.dto.response.ProductSearchResponse;
import domain.inventory.service.ProductService;
import global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Product", description = "화장품 마스터 조회 API")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

    private final ProductService productService;

    @Operation(
            summary = "화장품 마스터 전체 조회",
            description = "인벤토리가 아니라 마스터 DB(products)에 등록된 화장품 전체를 반환합니다. "
                    + "항목이 없으면 totalCount 0과 빈 배열을 반환합니다."
    )
    @GetMapping
    public ApiResponse<ProductListResponse> listAll() {
        return ApiResponse.success(productService.listAll());
    }

    @Operation(
            summary = "화장품 검색",
            description = "화장품명으로 마스터 DB를 검색합니다. 등록/자동 생성 로직은 포함하지 않으며, 일치하는 화장품이 없으면 빈 배열을 반환합니다."
    )
    @GetMapping("/search")
    public ApiResponse<ProductSearchResponse> search(
            @Parameter(description = "검색어", example = "달바") @RequestParam String keyword) {
        return ApiResponse.success(productService.search(keyword));
    }
}
