package domain.beauty.shortform.application;

import domain.beauty.domain.BeautyRoutineAnalysis.Step;
import domain.beauty.shortform.domain.IngredientDataStatus;
import domain.beauty.shortform.domain.ProductResolutionStatus;
import domain.inventory.ProductCategory;

public record MatchedVideoStep(
        Step source,
        Long productId,
        ProductCategory productCategory,
        String imageUrl,
        String displayBrand,
        String displayProductName,
        ProductResolutionStatus productResolutionStatus,
        double productResolutionConfidence,
        IngredientDataStatus ingredientDataStatus,
        ProductEnrichmentData enrichment
) {
}
