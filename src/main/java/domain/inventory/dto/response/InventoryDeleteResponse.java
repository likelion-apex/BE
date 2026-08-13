package domain.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인벤토리 삭제 결과")
public record InventoryDeleteResponse(
        @Schema(description = "인벤토리 ID") Long inventoryId,
        @Schema(description = "삭제 여부") boolean deleted
) {

    public static InventoryDeleteResponse of(Long inventoryId) {
        return new InventoryDeleteResponse(inventoryId, true);
    }
}
