package domain.beauty.shortform.application;

import domain.beauty.shortform.client.ProductEnrichmentResult;
import domain.beauty.shortform.domain.IngredientVerificationStatus;
import java.util.List;

public record ProductEnrichmentData(
        String displayBrand,
        String displayProductName,
        String marketOrVariant,
        double resolutionConfidence,
        IngredientVerificationStatus ingredientVerificationStatus,
        List<ProductEnrichmentResult.Source> sources,
        List<ProductEnrichmentResult.Ingredient> ingredients
) {
    public static ProductEnrichmentData unresolved() {
        return new ProductEnrichmentData(
                null, null, null, 0, IngredientVerificationStatus.UNVERIFIED, List.of(), List.of());
    }

    public boolean hasVerifiedIngredients() {
        return ingredientVerificationStatus != null
                && ingredientVerificationStatus.isAvailable()
                && ingredients != null
                && !ingredients.isEmpty();
    }
}
