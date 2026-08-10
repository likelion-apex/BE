package feat.apex_BE.cosmetic.service;

import feat.apex_BE.cosmetic.cache.RegulationInfoCache;
import feat.apex_BE.cosmetic.client.CsmtcsIngdCpntClient;
import feat.apex_BE.cosmetic.client.IngredientInfo;
import feat.apex_BE.cosmetic.client.KakaoImageClient;
import feat.apex_BE.cosmetic.client.OpenAiIngredientClient;
import feat.apex_BE.cosmetic.client.RegulationInfo;
import feat.apex_BE.cosmetic.dto.CosmeticInfoRequestDto;
import feat.apex_BE.cosmetic.dto.CosmeticInfoResponseDto;
import feat.apex_BE.cosmetic.dto.IngredientDetailDto;
import feat.apex_BE.cosmetic.dto.IngredientRegulationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CosmeticInfoService {

    private static final String SOURCE_USER_PROVIDED = "USER_PROVIDED";
    private static final String SOURCE_AI_GENERATED = "AI_GENERATED";

    private final KakaoImageClient kakaoImageClient;
    private final CsmtcsIngdCpntClient ingdCpntClient;
    private final RegulationInfoCache regulationInfoCache;
    private final OpenAiIngredientClient openAiIngredientClient;

    public CosmeticInfoResponseDto getCosmeticInfo(CosmeticInfoRequestDto request) {
        String imageUrl = kakaoImageClient.searchImageUrl(request.getProductName());

        boolean userProvidedIngredients = request.getIngredientNames() != null && !request.getIngredientNames().isEmpty();
        List<String> ingredientNames = userProvidedIngredients
                ? request.getIngredientNames()
                : openAiIngredientClient.fetchIngredientNames(request.getProductName());
        String ingredientSource = userProvidedIngredients ? SOURCE_USER_PROVIDED : SOURCE_AI_GENERATED;

        List<IngredientDetailDto> ingredients = ingredientNames.stream()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(this::buildIngredientDetail)
                .toList();

        return new CosmeticInfoResponseDto(request.getProductName(), imageUrl, ingredients, ingredientSource);
    }

    public IngredientDetailDto getIngredientDetail(String name) {
        return buildIngredientDetail(name.trim());
    }

    private IngredientDetailDto buildIngredientDetail(String name) {
        IngredientInfo info = ingdCpntClient.findByName(name);
        RegulationInfo regulation = regulationInfoCache.find(name).orElse(null);

        String englishName = info != null && info.englishName() != null
                ? info.englishName()
                : (regulation != null ? regulation.englishName() : null);

        IngredientRegulationDto regulationDto = new IngredientRegulationDto(
                regulation != null ? regulation.prohibitedCountries() : List.of(),
                regulation != null ? regulation.restrictedCountries() : List.of()
        );

        return new IngredientDetailDto(name, englishName, regulationDto);
    }
}
