package domain.ingredient.service;

import domain.ingredient.domain.IngredientInteraction;
import domain.ingredient.domain.InteractionType;
import domain.ingredient.domain.ProductIngredient;
import domain.ingredient.dto.request.ProductCompatibilityRequest;
import domain.ingredient.dto.response.ProductCompatibilityResponse;
import domain.ingredient.dto.response.ProductCompatibilityResponse.CompatibilityResult;
import domain.ingredient.repository.IngredientInteractionRepository;
import domain.ingredient.repository.ProductIngredientRepository;
import domain.ingredient.util.ProductCategoryDisplay;
import domain.inventory.Product;
import domain.inventory.ProductRepository;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제품 궁합 비교(4.3). 기준 제품 1개를 비교 대상 제품 여러 개와 1:N으로 비교한다.
 * 판단 우선순위: (1) 두 제품 성분 간 CONFLICT/SYNERGY 등록 여부 → (2) 카테고리 동일 + 공통 성분 존재 시 SUBSTITUTE → (3) NEUTRAL.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductCompatibilityService {

    private static final String NO_INTERACTION_DESCRIPTION = "특별한 상호작용이 확인되지 않았어요.";

    private final ProductRepository productRepository;
    private final ProductIngredientRepository productIngredientRepository;
    private final IngredientInteractionRepository ingredientInteractionRepository;

    public ProductCompatibilityResponse compare(ProductCompatibilityRequest request) {
        Product baseProduct = productRepository.findById(request.baseProductId())
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        Map<Long, Product> compareProductById = productRepository.findAllById(request.compareProductIds()).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        List<Product> compareProducts = request.compareProductIds().stream()
                .distinct()
                .map(compareProductById::get)
                .filter(Objects::nonNull)
                .toList();

        List<Long> allProductIds = new ArrayList<>();
        allProductIds.add(baseProduct.getId());
        compareProducts.forEach(product -> allProductIds.add(product.getId()));

        List<ProductIngredient> allProductIngredients = productIngredientRepository.findByProduct_IdIn(allProductIds);

        Map<Long, Set<Long>> ingredientIdsByProduct = allProductIngredients.stream()
                .collect(Collectors.groupingBy(
                        productIngredient -> productIngredient.getProduct().getId(),
                        Collectors.mapping(productIngredient -> productIngredient.getIngredient().getId(), Collectors.toSet())));

        Map<Long, String> ingredientNameById = allProductIngredients.stream()
                .collect(Collectors.toMap(
                        productIngredient -> productIngredient.getIngredient().getId(),
                        productIngredient -> productIngredient.getIngredient().getName(),
                        (existing, duplicate) -> existing));

        List<Long> unionIngredientIds = ingredientNameById.keySet().stream().toList();
        List<IngredientInteraction> interactionsInUnion = unionIngredientIds.isEmpty()
                ? List.of()
                : ingredientInteractionRepository.findAllAmong(unionIngredientIds);

        Set<Long> baseIngredientIds = ingredientIdsByProduct.getOrDefault(baseProduct.getId(), Set.of());

        List<CompatibilityResult> results = compareProducts.stream()
                .map(compareProduct -> buildResult(
                        baseProduct,
                        baseIngredientIds,
                        compareProduct,
                        ingredientIdsByProduct.getOrDefault(compareProduct.getId(), Set.of()),
                        interactionsInUnion,
                        ingredientNameById))
                .toList();

        return new ProductCompatibilityResponse(baseProduct.getId(), baseProduct.getName(), baseProduct.getCategory(), results);
    }

    private CompatibilityResult buildResult(
            Product baseProduct,
            Set<Long> baseIngredientIds,
            Product compareProduct,
            Set<Long> compareIngredientIds,
            List<IngredientInteraction> interactionsInUnion,
            Map<Long, String> ingredientNameById) {

        List<IngredientInteraction> crossInteractions = interactionsInUnion.stream()
                .filter(interaction -> isCrossPair(interaction, baseIngredientIds, compareIngredientIds))
                .toList();

        IngredientInteraction conflict = crossInteractions.stream()
                .filter(interaction -> interaction.getInteractionType() == InteractionType.CONFLICT)
                .findFirst()
                .orElse(null);
        if (conflict != null) {
            return toResult(compareProduct, InteractionType.CONFLICT, describeInteraction(conflict, ingredientNameById));
        }

        IngredientInteraction synergy = crossInteractions.stream()
                .filter(interaction -> interaction.getInteractionType() == InteractionType.SYNERGY)
                .findFirst()
                .orElse(null);
        if (synergy != null) {
            return toResult(compareProduct, InteractionType.SYNERGY, describeInteraction(synergy, ingredientNameById));
        }

        boolean sameCategory = baseProduct.getCategory() != null && baseProduct.getCategory() == compareProduct.getCategory();
        boolean hasCommonIngredient = baseIngredientIds.stream().anyMatch(compareIngredientIds::contains);
        if (sameCategory && hasCommonIngredient) {
            String description = "%s 카테고리 제품으로 공통 성분이 있어 서로 대체할 수 있는 호환 관계예요."
                    .formatted(ProductCategoryDisplay.toKorean(baseProduct.getCategory()));
            return toResult(compareProduct, InteractionType.SUBSTITUTE, description);
        }

        return toResult(compareProduct, InteractionType.NEUTRAL, NO_INTERACTION_DESCRIPTION);
    }

    private boolean isCrossPair(IngredientInteraction interaction, Set<Long> baseIds, Set<Long> compareIds) {
        Long ingredientAId = interaction.getIngredientAId();
        Long ingredientBId = interaction.getIngredientBId();
        return (baseIds.contains(ingredientAId) && compareIds.contains(ingredientBId))
                || (baseIds.contains(ingredientBId) && compareIds.contains(ingredientAId));
    }

    private String describeInteraction(IngredientInteraction interaction, Map<Long, String> ingredientNameById) {
        if (interaction.getDescription() != null && !interaction.getDescription().isBlank()) {
            return interaction.getDescription();
        }
        String nameA = ingredientNameById.getOrDefault(interaction.getIngredientAId(), "성분");
        String nameB = ingredientNameById.getOrDefault(interaction.getIngredientBId(), "성분");
        return "%s와(과) %s 성분 조합이에요.".formatted(nameA, nameB);
    }

    private CompatibilityResult toResult(Product compareProduct, InteractionType interactionType, String description) {
        return new CompatibilityResult(
                compareProduct.getId(), compareProduct.getName(), compareProduct.getCategory(), interactionType, description);
    }
}