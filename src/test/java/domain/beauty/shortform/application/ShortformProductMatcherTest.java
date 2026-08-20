package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import domain.beauty.domain.BeautyRoutineAnalysis.EvidenceSource;
import domain.beauty.domain.BeautyRoutineAnalysis.IdentificationLevel;
import domain.beauty.domain.BeautyRoutineAnalysis.PurposeBasis;
import domain.beauty.domain.BeautyRoutineAnalysis.Step;
import domain.beauty.shortform.client.ProductEnrichmentResult;
import domain.beauty.shortform.domain.IngredientDataStatus;
import domain.beauty.shortform.domain.IngredientSourceType;
import domain.beauty.shortform.domain.IngredientVerificationStatus;
import domain.beauty.shortform.domain.ProductResolutionStatus;
import domain.cosmetic.client.KakaoImageClient;
import domain.inventory.ProductRepository;
import domain.inventory.ProductCategory;
import domain.inventory.CategoryImageResolver;
import global.util.PublicUrlResolver;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShortformProductMatcherTest {

    @Test
    void exposesEstimatedIngredientsEvenWhenVideoStepWasCategoryOnly() {
        ProductRepository repository = mock(ProductRepository.class);
        KakaoImageClient imageClient = mock(KakaoImageClient.class);
        when(repository.findFirstByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(repository.findFirstByNameIgnoreCaseAndBrandIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(imageClient.searchImageUrl(anyString())).thenReturn(null);
        ShortformProductMatcher matcher = new ShortformProductMatcher(
                repository, imageClient, new ShortformProductCategoryResolver(),
                new ShortformProductImageResolver(
                        new CategoryImageResolver(), new PublicUrlResolver("https://mutsa.dev.me.kr")));
        ProductEnrichmentData estimated = new ProductEnrichmentData(
                "토리든",
                "다이브인 저분자 히알루론산 수딩 크림",
                "한국 판매 처방 추정",
                0.72,
                IngredientVerificationStatus.ESTIMATED,
                List.of(new ProductEnrichmentResult.Source(
                        "https://example.com/product", "product", IngredientSourceType.RETAILER)),
                List.of(new ProductEnrichmentResult.Ingredient(
                        1, "정제수", List.of("용제"), List.of("수분 공급"), 1, false, false))
        );

        MatchedVideoStep matched = matcher.match(List.of(categoryStep()), Map.of(1, estimated)).getFirst();

        assertThat(matched.ingredientDataStatus()).isEqualTo(IngredientDataStatus.AVAILABLE);
        assertThat(matched.productResolutionStatus()).isEqualTo(ProductResolutionStatus.AI_NORMALIZED);
        assertThat(matched.displayProductName()).contains("다이브인");
        assertThat(matched.productCategory()).isEqualTo(ProductCategory.CREAM);
        assertThat(matched.imageUrl())
                .isEqualTo("https://mutsa.dev.me.kr/images/categories/cream.png");
        verifyNoInteractions(imageClient);
    }

    private Step categoryStep() {
        return new Step(
                1, "00:01", null, "얼굴", "도포", "흡수", "보습",
                PurposeBasis.GENERAL_INFERENCE, null, IdentificationLevel.CATEGORY_ONLY,
                "수딩 크림", null, null, null, null, null,
                List.of(EvidenceSource.VISUAL_USAGE), "파란 용기의 수딩 크림", 0.70);
    }
}
