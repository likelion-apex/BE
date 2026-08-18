package domain.beauty.shortform.application;

import domain.beauty.shortform.client.ProductEnrichmentResult;
import domain.beauty.shortform.domain.IngredientVerificationStatus;
import java.util.List;

public record InventoryProductEvidence(
        IngredientVerificationStatus verificationStatus,
        List<ProductEnrichmentResult.Ingredient> ingredients
) {
    public static InventoryProductEvidence unavailable() {
        return new InventoryProductEvidence(IngredientVerificationStatus.UNVERIFIED, List.of());
    }

    public boolean isAvailable() {
        return verificationStatus != null
                && verificationStatus.isAvailable()
                && ingredients != null
                && !ingredients.isEmpty();
    }
}
