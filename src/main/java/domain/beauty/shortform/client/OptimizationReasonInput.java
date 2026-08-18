package domain.beauty.shortform.client;

import domain.beauty.shortform.domain.IngredientDataStatus;
import domain.beauty.shortform.domain.OptimizationStatus;
import java.util.List;

public record OptimizationReasonInput(
        MemberProfile member,
        List<Step> steps
) {
    public record MemberProfile(String skinType, List<String> skinConcerns) {
    }

    public record Step(
            int order,
            OptimizationStatus status,
            Product videoProduct,
            Product inventoryProduct
    ) {
    }

    public record Product(
            String category,
            String productName,
            String purpose,
            List<String> keyBenefits,
            IngredientDataStatus ingredientDataStatus,
            List<Ingredient> ingredients
    ) {
    }

    public record Ingredient(
            String name,
            List<String> purposes,
            List<String> skinBenefits
    ) {
    }
}
