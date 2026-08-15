package domain.beauty.shortform.application;

import domain.beauty.domain.BeautyRoutineAnalysis.Step;
import domain.beauty.shortform.domain.IngredientDataStatus;
import domain.beauty.shortform.domain.ProductResolutionStatus;

public record MatchedVideoStep(
        Step source,
        Long productId,
        String imageUrl,
        String displayBrand,
        String displayProductName,
        ProductResolutionStatus productResolutionStatus,
        double productResolutionConfidence,
        IngredientDataStatus ingredientDataStatus,
        ProductEnrichmentData enrichment
) {
}
