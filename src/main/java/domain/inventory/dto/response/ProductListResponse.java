package domain.inventory.dto.response;

import domain.inventory.Product;
import domain.inventory.ProductCategory;
import global.util.PublicUrlResolver;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "화장품 마스터 전체 목록")
public record ProductListResponse(
        @Schema(description = "전체 상품 개수") long totalCount,
        @Schema(description = "상품 목록") List<ProductListItem> items
) {

    public static ProductListResponse from(List<Product> products, PublicUrlResolver publicUrlResolver) {
        return new ProductListResponse(
                products.size(),
                products.stream().map(product -> ProductListItem.from(product, publicUrlResolver)).toList()
        );
    }

    @Schema(description = "화장품 마스터 한 건")
    public record ProductListItem(
            @Schema(description = "상품 ID") Long productId,
            @Schema(description = "제품명") String productName,
            @Schema(description = "브랜드") String brand,
            @Schema(description = "카테고리") ProductCategory category,
            @Schema(description = "이미지 URL") String imageUrl
    ) {

        public static ProductListItem from(Product product, PublicUrlResolver publicUrlResolver) {
            return new ProductListItem(
                    product.getId(),
                    product.getName(),
                    product.getBrand(),
                    product.getCategory(),
                    publicUrlResolver.resolve(product.getImageUrl())
            );
        }
    }
}
