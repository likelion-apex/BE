package domain.beauty.shortform.application;

import domain.beauty.domain.BeautyRoutineAnalysis.IdentificationLevel;
import domain.beauty.domain.BeautyRoutineAnalysis.Step;
import domain.cosmetic.client.KakaoImageClient;
import domain.inventory.Product;
import domain.inventory.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ShortformProductMatcher {

    private final ProductRepository productRepository;
    private final KakaoImageClient imageClient;

    public ShortformProductMatcher(ProductRepository productRepository, KakaoImageClient imageClient) {
        this.productRepository = productRepository;
        this.imageClient = imageClient;
    }

    public List<MatchedVideoStep> match(List<Step> steps) {
        return steps.stream().map(this::match).toList();
    }

    private MatchedVideoStep match(Step step) {
        if (step.identificationLevel() != IdentificationLevel.EXACT_PRODUCT || step.productName() == null) {
            return new MatchedVideoStep(step, null, null);
        }
        Product product = productRepository.findFirstByNameIgnoreCase(step.productName()).orElse(null);
        if (product != null) {
            return new MatchedVideoStep(step, product.getId(), product.getImageUrl());
        }
        String query = step.brand() == null ? step.productName() : step.brand() + " " + step.productName();
        return new MatchedVideoStep(step, null, imageClient.searchImageUrl(query));
    }
}
