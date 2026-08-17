package domain.beauty.shortform.client;

import domain.beauty.shortform.domain.IngredientDataStatus;
import java.util.List;

public record RoutinePersonalizationInput(
        MemberProfile member,
        VideoContext video,
        List<VideoStep> steps,
        List<InventoryProduct> inventory
) {
    public record MemberProfile(String skinType, List<String> skinConcerns) {
    }

    public record VideoContext(String videoId, String summary, List<String> warnings) {
    }

    public record VideoStep(
            int order,
            String category,
            String brand,
            String productName,
            String displayBrand,
            String displayProductName,
            String purpose,
            String evidenceSummary,
            double confidence,
            Long matchedProductId,
            IngredientDataStatus ingredientDataStatus,
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

    public record InventoryProduct(
            Long inventoryId,
            Long productId,
            String category,
            String brand,
            String productName
    ) {
    }
}
