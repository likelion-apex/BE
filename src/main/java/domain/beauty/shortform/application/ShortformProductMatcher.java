package domain.beauty.shortform.application;

import domain.beauty.domain.BeautyRoutineAnalysis.IdentificationLevel;
import domain.beauty.domain.BeautyRoutineAnalysis.Step;
import domain.beauty.shortform.domain.IngredientDataStatus;
import domain.beauty.shortform.domain.ProductResolutionStatus;
import domain.cosmetic.client.KakaoImageClient;
import domain.inventory.Product;
import domain.inventory.ProductCategory;
import domain.inventory.ProductRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ShortformProductMatcher {

    private final ProductRepository productRepository;
    private final KakaoImageClient imageClient;
    private final ShortformProductCategoryResolver categoryResolver;

    public ShortformProductMatcher(
            ProductRepository productRepository,
            KakaoImageClient imageClient,
            ShortformProductCategoryResolver categoryResolver
    ) {
        this.productRepository = productRepository;
        this.imageClient = imageClient;
        this.categoryResolver = categoryResolver;
    }

    public List<MatchedVideoStep> match(
            List<Step> steps,
            Map<Integer, ProductEnrichmentData> enrichments
    ) {
        return steps.stream()
                .map(step -> match(step, enrichments.getOrDefault(step.order(), ProductEnrichmentData.unresolved())))
                .toList();
    }

    private MatchedVideoStep match(Step step, ProductEnrichmentData enrichment) {
        boolean videoHasExactProduct = step.identificationLevel() == IdentificationLevel.EXACT_PRODUCT
                && step.productName() != null;
        boolean aiResolvedProduct = enrichment.displayProductName() != null
                && enrichment.resolutionConfidence() >= 0.60;
        if (!videoHasExactProduct && !aiResolvedProduct) {
            return new MatchedVideoStep(
                    step, null, categoryResolver.resolve(step.category(), step.productName()), null,
                    step.brand(), step.category(), ProductResolutionStatus.UNRESOLVED,
                    0, IngredientDataStatus.NOT_ELIGIBLE, ProductEnrichmentData.unresolved());
        }

        Product product = findCatalogProduct(step, enrichment);
        String displayBrand = product != null
                ? textOr(product.getBrand(), textOr(enrichment.displayBrand(), step.brand()))
                : textOr(enrichment.displayBrand(), step.brand());
        String displayProductName = product != null
                ? textOr(product.getName(), textOr(enrichment.displayProductName(), step.productName()))
                : textOr(enrichment.displayProductName(), textOr(step.productName(), step.category()));
        ProductResolutionStatus resolutionStatus = product != null
                ? ProductResolutionStatus.CATALOG_MATCH
                : aiResolvedProduct
                        ? ProductResolutionStatus.AI_NORMALIZED
                        : ProductResolutionStatus.VIDEO_LITERAL;
        double resolutionConfidence = product != null ? 1 : Math.max(step.confidence(), enrichment.resolutionConfidence());
        IngredientDataStatus ingredientStatus = enrichment.hasVerifiedIngredients()
                ? IngredientDataStatus.AVAILABLE
                : !videoHasExactProduct || enrichment.displayProductName() == null
                        ? IngredientDataStatus.NOT_ELIGIBLE
                        : IngredientDataStatus.UNAVAILABLE;

        if (product != null) {
            return new MatchedVideoStep(
                    step,
                    product.getId(),
                    product.getCategory() == null ? ProductCategory.ETC : product.getCategory(),
                    product.getImageUrl(),
                    displayBrand,
                    displayProductName,
                    resolutionStatus,
                    resolutionConfidence,
                    ingredientStatus,
                    enrichment
            );
        }
        String query = displayBrand == null ? displayProductName : displayBrand + " " + displayProductName;
        return new MatchedVideoStep(
                step,
                null,
                categoryResolver.resolve(step.category(), displayProductName),
                imageClient.searchImageUrl(query),
                displayBrand,
                displayProductName,
                resolutionStatus,
                resolutionConfidence,
                ingredientStatus,
                enrichment
        );
    }

    private Product findCatalogProduct(Step step, ProductEnrichmentData enrichment) {
        Product rawMatch = step.productName() == null
                ? null
                : step.brand() == null
                        ? productRepository.findFirstByNameIgnoreCase(step.productName()).orElse(null)
                        : productRepository.findFirstByNameIgnoreCaseAndBrandIgnoreCase(
                                step.productName(), step.brand()).orElse(null);
        if (rawMatch != null) {
            return rawMatch;
        }
        if (enrichment.displayProductName() == null) {
            return null;
        }
        if (enrichment.displayBrand() != null) {
            Product normalizedWithBrand = productRepository.findFirstByNameIgnoreCaseAndBrandIgnoreCase(
                    enrichment.displayProductName(), enrichment.displayBrand()).orElse(null);
            if (normalizedWithBrand != null) {
                return normalizedWithBrand;
            }
        }
        return productRepository.findFirstByNameIgnoreCase(enrichment.displayProductName()).orElse(null);
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
