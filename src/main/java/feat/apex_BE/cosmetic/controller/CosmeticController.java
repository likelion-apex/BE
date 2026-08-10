package feat.apex_BE.cosmetic.controller;

import feat.apex_BE.cosmetic.dto.CosmeticInfoRequestDto;
import feat.apex_BE.cosmetic.dto.CosmeticInfoResponseDto;
import feat.apex_BE.cosmetic.dto.IngredientDetailDto;
import feat.apex_BE.cosmetic.service.CosmeticInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cosmetics")
@RequiredArgsConstructor
@Tag(name = "Cosmetic", description = "화장품 성분정보 관련 API (원료성분정보 + 규제정보 조합)")
public class CosmeticController {

    private static final String INFO_REQUEST_EXAMPLE = """
            {
              "productName": "이니스프리 그린티씨드크림"
            }
            """;

    private static final String INFO_REQUEST_WITH_INGREDIENTS_EXAMPLE = """
            {
              "productName": "이니스프리 그린티씨드크림",
              "ingredientNames": ["정제수", "글리세린", "코코트라이모늄클로라이드"]
            }
            """;

    private static final String INFO_RESPONSE_EXAMPLE = """
            {
              "productName": "이니스프리 그린티씨드크림",
              "imageUrl": "https://search1.kakaocdn.net/argon/130x130_85_c/example.jpg",
              "ingredientSource": "AI_GENERATED",
              "ingredients": [
                {
                  "name": "정제수",
                  "englishName": "Water",
                  "regulation": { "prohibitedCountries": [], "restrictedCountries": [] }
                },
                {
                  "name": "글리세린",
                  "englishName": "Glycerin",
                  "regulation": { "prohibitedCountries": [], "restrictedCountries": [] }
                },
                {
                  "name": "코코트라이모늄클로라이드",
                  "englishName": "Cocotrimonium Chloride",
                  "regulation": { "prohibitedCountries": [], "restrictedCountries": ["EU", "브라질", "아르헨티나", "아세안", "중국", "한국"] }
                }
              ]
            }
            """;

    private static final String INGREDIENT_RESPONSE_EXAMPLE = """
            {
              "name": "글리세린",
              "englishName": "Glycerin",
              "regulation": { "prohibitedCountries": [], "restrictedCountries": [] }
            }
            """;

    private final CosmeticInfoService cosmeticInfoService;

    @PostMapping("/info")
    @Operation(
            summary = "화장품 정보 조회",
            description = "제품명을 받아 카카오 이미지 검색 결과와 성분별 영문명/규제정보(금지·제한국가)를 조합해 반환합니다. "
                    + "전성분 목록(ingredientNames)을 생략하면 ChatGPT를 통해 자동으로 조회하며, 이 경우 응답의 ingredientSource는 "
                    + "AI_GENERATED로 표시됩니다. 전성분 목록을 직접 보내면 해당 값을 그대로 사용하고 ingredientSource는 USER_PROVIDED가 됩니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = {
                            @ExampleObject(name = "제품명만 전달 (전성분 자동조회)", value = INFO_REQUEST_EXAMPLE),
                            @ExampleObject(name = "전성분 목록 직접 전달", value = INFO_REQUEST_WITH_INGREDIENTS_EXAMPLE)
                    })
            )
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(examples = @ExampleObject(name = "응답 예시 (AI_GENERATED)", value = INFO_RESPONSE_EXAMPLE))
    )
    public ResponseEntity<CosmeticInfoResponseDto> getCosmeticInfo(@Valid @RequestBody CosmeticInfoRequestDto request) {
        return ResponseEntity.ok(cosmeticInfoService.getCosmeticInfo(request));
    }

    @GetMapping("/ingredients/{name}")
    @Operation(
            summary = "성분 단건 조회",
            description = "성분명으로 원료성분정보(영문명)와 규제정보(금지·제한국가)를 조회합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(examples = @ExampleObject(name = "응답 예시", value = INGREDIENT_RESPONSE_EXAMPLE))
    )
    public ResponseEntity<IngredientDetailDto> getIngredient(
            @Parameter(description = "성분명", example = "글리세린") @PathVariable String name) {
        return ResponseEntity.ok(cosmeticInfoService.getIngredientDetail(name));
    }
}
