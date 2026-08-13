package domain.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "즐겨찾기 등록/해제 요청")
public record FavoriteUpdateRequest(

        @Schema(description = "즐겨찾기 등록 여부", example = "true")
        @NotNull(message = "isFavorite는 필수입니다.")
        Boolean isFavorite
) {
}
