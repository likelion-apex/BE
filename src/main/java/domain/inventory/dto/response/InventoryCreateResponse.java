package domain.inventory.dto.response;

import domain.inventory.Inventory;
import domain.inventory.OwnType;
import domain.inventory.ProductCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "인벤토리 추가 결과")
public record InventoryCreateResponse(
        @Schema(description = "인벤토리 ID") Long inventoryId,
        @Schema(description = "상품 ID") Long productId,
        @Schema(description = "제품명") String productName,
        @Schema(description = "브랜드") String brand,
        @Schema(description = "카테고리 (AI 자동 분류)") ProductCategory category,
        @Schema(description = "보유/위시 여부") OwnType ownType,
        @Schema(description = "즐겨찾기 여부") boolean isFavorite,
        @Schema(description = "등록 일시") LocalDateTime createdAt
) {

    public static InventoryCreateResponse from(Inventory inventory) {
        return new InventoryCreateResponse(
                inventory.getId(),
                inventory.getProduct().getId(),
                inventory.getProduct().getName(),
                inventory.getProduct().getBrand(),
                inventory.getProduct().getCategory(),
                inventory.getOwnType(),
                inventory.isFavorite(),
                inventory.getCreatedAt()
        );
    }
}
