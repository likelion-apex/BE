package domain.inventory.dto.response;

import domain.inventory.Product;
import domain.inventory.ProductCategory;
import global.util.PublicUrlResolver;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "화장품 검색 결과")
public record ProductSearchResponse(
        @Schema(description = "검색된 화장품 목록") List<ProductSearchItem> items
) {

    public static ProductSearchResponse from(List<Product> products, PublicUrlResolver publicUrlResolver) {
        return new ProductSearchResponse(
                products.stream().map(product -> ProductSearchItem.from(product, publicUrlResolver)).toList());
    }

    @Schema(description = "검색된 화장품 한 건")
    public record ProductSearchItem(
            @Schema(description = "상품 ID") Long productId,
            @Schema(description = "제품명") String productName,
            @Schema(description = "브랜드") String brand,
            @Schema(description = "카테고리") ProductCategory category,
            @Schema(description = "이미지 URL") String imageUrl
    ) {

        public static ProductSearchItem from(Product product, PublicUrlResolver publicUrlResolver) {
            return new ProductSearchItem(
                    product.getId(),
                    product.getName(),
                    product.getBrand(),
                    product.getCategory(),
                    publicUrlResolver.resolve(product.getImageUrl())
            );
        }
    }
}
