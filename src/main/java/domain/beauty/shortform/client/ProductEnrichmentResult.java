package domain.beauty.shortform.client;

import java.util.List;

public record ProductEnrichmentResult(List<Product> products) {

    public record Product(
            String requestKey,
            String displayBrand,
            String displayProductName,
            double resolutionConfidence,
            List<Ingredient> ingredients
    ) {
    }

    public record Ingredient(
            int order,
            String name,
            List<String> purposes,
            List<String> skinBenefits,
            Integer riskScore,
            boolean caution20,
            boolean allergen
    ) {
    }

    public record Response(
            ProductEnrichmentResult result,
            String model,
            long inputTokens,
            long outputTokens
    ) {
    }
}
