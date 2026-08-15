package domain.beauty.shortform.client;

import domain.beauty.shortform.domain.IngredientSourceType;
import java.util.List;

public record ProductEnrichmentResult(List<Product> products) {

    public record Product(
            String requestKey,
            String displayBrand,
            String displayProductName,
            String marketOrVariant,
            LookupStatus lookupStatus,
            double resolutionConfidence,
            String notes,
            List<Source> sources,
            List<Ingredient> ingredients
    ) {
    }

    public enum LookupStatus {
        FOUND,
        ESTIMATED,
        AMBIGUOUS,
        NOT_FOUND
    }

    public record Source(
            String url,
            String title,
            IngredientSourceType sourceType
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
            long outputTokens,
            int webSearchCalls,
            List<WebSource> webSources
    ) {
        public Response(
                ProductEnrichmentResult result,
                String model,
                long inputTokens,
                long outputTokens
        ) {
            this(result, model, inputTokens, outputTokens, 0, List.of());
        }
    }

    public record WebSource(
            String url,
            String title
    ) {
    }
}
