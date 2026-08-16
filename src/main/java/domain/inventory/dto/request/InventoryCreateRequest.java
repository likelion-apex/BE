package domain.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "인벤토리 추가 요청")
public record InventoryCreateRequest(

        @Schema(description = "제품명 (마스터 DB에 이미 있으면 재사용하고, 없으면 신규 등록하며 이때 이미지 검색과 카테고리 AI 자동 분류가 함께 수행됩니다)",
                example = "달바 화이트 트러플 퍼스트 스프레이 세럼")
        @NotBlank(message = "productName은 필수입니다.")
        String productName
) {
}
