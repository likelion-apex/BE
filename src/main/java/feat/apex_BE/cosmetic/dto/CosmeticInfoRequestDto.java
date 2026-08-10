package feat.apex_BE.cosmetic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CosmeticInfoRequestDto {

    @Schema(description = "제품명", example = "이니스프리 그린티씨드크림")
    @NotBlank(message = "제품명은 필수입니다.")
    private String productName;

    @Schema(description = "전성분 목록 (제품 라벨에 기재된 성분명). 생략하면 ChatGPT를 통해 자동으로 조회합니다.",
            example = "[\"정제수\", \"글리세린\", \"코코트라이모늄클로라이드\"]")
    private List<String> ingredientNames;
}
