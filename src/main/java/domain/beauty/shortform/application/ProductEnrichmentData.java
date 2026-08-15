package domain.beauty.shortform.application;

import domain.beauty.shortform.client.ProductEnrichmentResult;
import java.util.List;

public record ProductEnrichmentData(
        String displayBrand,
        String displayProductName,
        double resolutionConfidence,
        List<ProductEnrichmentResult.Ingredient> ingredients
) {
    public static ProductEnrichmentData unresolved() {
        return new ProductEnrichmentData(null, null, 0, List.of());
    }
}
