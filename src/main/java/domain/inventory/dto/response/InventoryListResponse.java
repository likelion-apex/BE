package domain.inventory.dto.response;

import domain.inventory.Inventory;
import domain.inventory.ProductCategory;
import global.util.PublicUrlResolver;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "인벤토리 전체 목록")
public record InventoryListResponse(
        @Schema(description = "전체 등록 개수") long totalCount,
        @Schema(description = "인벤토리 목록") List<InventoryListItem> items
) {

    public static InventoryListResponse from(List<Inventory> inventories, PublicUrlResolver publicUrlResolver) {
        return new InventoryListResponse(
                inventories.size(),
                inventories.stream().map(inventory -> InventoryListItem.from(inventory, publicUrlResolver)).toList()
        );
    }

    @Schema(description = "인벤토리 한 건")
    public record InventoryListItem(
            @Schema(description = "인벤토리 ID") Long inventoryId,
            @Schema(description = "상품 ID") Long productId,
            @Schema(description = "제품명") String productName,
            @Schema(description = "브랜드") String brand,
            @Schema(description = "카테고리") ProductCategory category,
            @Schema(description = "이미지 URL") String imageUrl,
            @Schema(description = "즐겨찾기 여부") boolean isFavorite,
            @Schema(description = "등록 일시") LocalDateTime createdAt
    ) {

        public static InventoryListItem from(Inventory inventory, PublicUrlResolver publicUrlResolver) {
            return new InventoryListItem(
                    inventory.getId(),
                    inventory.getProduct().getId(),
                    inventory.getProduct().getName(),
                    inventory.getProduct().getBrand(),
                    inventory.getProduct().getCategory(),
                    publicUrlResolver.resolve(inventory.getProduct().getImageUrl()),
                    inventory.isFavorite(),
                    inventory.getCreatedAt()
            );
        }
    }
}
