package feat.apex_BE.cosmetic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

@Getter
public class CosmeticInfoResponseDto {

    @Schema(description = "제품명", example = "이니스프리 그린티씨드크림")
    private final String productName;

    @Schema(description = "제품 이미지 URL (카카오 이미지 검색 결과, 없으면 null)")
    private final String imageUrl;

    @Schema(description = "전성분 목록별 상세정보")
    private final List<IngredientDetailDto> ingredients;

    @Schema(description = "전성분 목록 출처. USER_PROVIDED(요청에 포함된 목록) 또는 AI_GENERATED(ChatGPT 자동 추출)", example = "AI_GENERATED")
    private final String ingredientSource;

    public CosmeticInfoResponseDto(String productName, String imageUrl, List<IngredientDetailDto> ingredients, String ingredientSource) {
        this.productName = productName;
        this.imageUrl = imageUrl;
        this.ingredients = ingredients;
        this.ingredientSource = ingredientSource;
    }
}
