package domain.cosmetic.service;

import domain.cosmetic.cache.RegulationInfoCache;
import domain.cosmetic.client.CsmtcsIngdCpntClient;
import domain.cosmetic.client.IngredientInfo;
import domain.cosmetic.client.KakaoImageClient;
import domain.inventory.ai.IngredientAiClient;
import domain.cosmetic.client.RegulationInfo;
import domain.cosmetic.dto.CosmeticInfoRequestDto;
import domain.cosmetic.dto.CosmeticInfoResponseDto;
import domain.cosmetic.dto.IngredientDetailDto;
import domain.cosmetic.dto.IngredientRegulationDto;
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
    private final IngredientAiClient ingredientAiClient;

    public CosmeticInfoResponseDto getCosmeticInfo(CosmeticInfoRequestDto request) {
        String imageUrl = kakaoImageClient.searchImageUrl(request.getProductName());

        boolean userProvidedIngredients = request.getIngredientNames() != null && !request.getIngredientNames().isEmpty();
        List<String> ingredientNames = userProvidedIngredients
                ? request.getIngredientNames()
                : ingredientAiClient.fetchIngredientNames(request.getProductName());
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
