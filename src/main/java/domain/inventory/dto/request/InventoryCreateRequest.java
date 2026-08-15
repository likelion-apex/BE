package domain.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인벤토리 추가 요청")
public record InventoryCreateRequest(

        @Schema(description = "기존 상품 ID (화장품 검색으로 조회한 상품일 때 사용)", example = "1024")
        Long productId,

        @Schema(description = "마스터 DB에 없는 신규 상품의 제품명 (productId가 없을 때 사용, 최초 등록 시 카테고리는 AI가 자동 분류)",
                example = "달바 화이트 트러플 퍼스트 스프레이 세럼")
        String productName
) {
}
