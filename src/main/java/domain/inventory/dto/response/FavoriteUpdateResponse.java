package domain.inventory.dto.response;

import domain.inventory.Inventory;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "즐겨찾기 등록/해제 결과")
public record FavoriteUpdateResponse(
        @Schema(description = "인벤토리 ID") Long inventoryId,
        @Schema(description = "즐겨찾기 여부") boolean isFavorite
) {

    public static FavoriteUpdateResponse from(Inventory inventory) {
        return new FavoriteUpdateResponse(inventory.getId(), inventory.isFavorite());
    }
}
