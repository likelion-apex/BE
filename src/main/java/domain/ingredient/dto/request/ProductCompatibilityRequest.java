package domain.ingredient.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "제품 궁합 비교 요청")
public record ProductCompatibilityRequest(
        @Schema(description = "기준 제품 ID") @NotNull Long baseProductId,
        @Schema(description = "비교할 제품 ID 목록 (1개 이상)") @NotEmpty List<Long> compareProductIds
) {
}