package domain.inventory.dto.response;

import domain.inventory.Inventory;
import domain.inventory.ProductCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "즐겨찾는 화장품 목록 (홈 화면 노출용)")
public record FavoriteInventoryResponse(
        @Schema(description = "전체 즐겨찾기 개수") long totalFavoriteCount,
        @Schema(description = "즐겨찾기 목록") List<FavoriteInventoryItem> items
) {

    public static FavoriteInventoryResponse of(long totalFavoriteCount, List<Inventory> favorites) {
        return new FavoriteInventoryResponse(
                totalFavoriteCount,
                favorites.stream().map(FavoriteInventoryItem::from).toList()
        );
    }

    @Schema(description = "즐겨찾는 화장품 한 건")
    public record FavoriteInventoryItem(
            @Schema(description = "인벤토리 ID") Long inventoryId,
            @Schema(description = "상품 ID") Long productId,
            @Schema(description = "제품명") String productName,
            @Schema(description = "브랜드") String brand,
            @Schema(description = "카테고리") ProductCategory category,
            @Schema(description = "이미지 URL") String imageUrl,
            @Schema(description = "즐겨찾기 여부") boolean isFavorite
    ) {

        public static FavoriteInventoryItem from(Inventory inventory) {
            return new FavoriteInventoryItem(
                    inventory.getId(),
                    inventory.getProduct().getId(),
                    inventory.getProduct().getName(),
                    inventory.getProduct().getBrand(),
                    inventory.getProduct().getCategory(),
                    inventory.getProduct().getImageUrl(),
                    inventory.isFavorite()
            );
        }
    }
}
