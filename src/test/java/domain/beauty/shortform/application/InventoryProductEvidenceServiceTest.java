package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import domain.beauty.shortform.application.ShortformAnalysisStateService.InventoryFact;
import domain.ingredient.domain.Ingredient;
import domain.ingredient.domain.ProductIngredient;
import domain.ingredient.repository.ProductIngredientRepository;
import domain.inventory.Product;
import domain.inventory.ProductCategory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InventoryProductEvidenceServiceTest {

    private final ProductIngredientRepository repository = mock(ProductIngredientRepository.class);
    private final ShortformProductEnrichmentService enrichmentService = mock(ShortformProductEnrichmentService.class);
    private final InventoryProductEvidenceService service = new InventoryProductEvidenceService(
            repository, enrichmentService, new ShortformProductCategoryResolver());

    @Test
    void usesStoredIngredientsBeforeCallingExternalEnrichment() {
        Product product = Product.builder()
                .name("보유 수분 앰플")
                .brand("테스트")
                .category(ProductCategory.ESSENCE_SERUM)
                .build();
        ReflectionTestUtils.setField(product, "id", 20L);
        Ingredient ingredient = Ingredient.builder()
                .name("히알루론산")
                .functionTag("보습")
                .functionGroup("수분 공급")
                .ewgGrade(1)
                .build();
        ProductIngredient mapping = ProductIngredient.builder()
                .product(product)
                .ingredient(ingredient)
                .rank(1)
                .build();
        when(repository.findByProduct_IdIn(anyList())).thenReturn(List.of(mapping));

        Map<Long, InventoryProductEvidence> result = service.enrichMatchingCategories(
                List.of(new InventoryFact(100L, 20L, "보유 수분 앰플", "테스트", "ESSENCE_SERUM", null)),
                Set.of(ProductCategory.ESSENCE_SERUM));

        assertThat(result.get(20L).ingredients()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("히알루론산");
            assertThat(item.purposes()).contains("보습", "수분 공급");
        });
        verifyNoInteractions(enrichmentService);
    }

    @Test
    void excludesOtherCategoriesAndEtcBeforeLookup() {
        Map<Long, InventoryProductEvidence> result = service.enrichMatchingCategories(
                List.of(
                        new InventoryFact(100L, 20L, "보유 토너", "테스트", "SKIN_TONER", null),
                        new InventoryFact(101L, 21L, "보유 기타", "테스트", "ETC", null)),
                Set.of(ProductCategory.ESSENCE_SERUM));

        assertThat(result).isEmpty();
        verifyNoInteractions(repository, enrichmentService);
    }

    @Test
    void enrichesOnlyStoredIngredientMissesAndReturnsCachedEvidenceShape() {
        when(repository.findByProduct_IdIn(anyList())).thenReturn(List.of());
        ProductEnrichmentData enriched = new ProductEnrichmentData(
                "테스트",
                "보유 수분 앰플",
                null,
                0.95,
                domain.beauty.shortform.domain.IngredientVerificationStatus.CORROBORATED,
                List.of(),
                List.of(new domain.beauty.shortform.client.ProductEnrichmentResult.Ingredient(
                        1, "히알루론산", List.of("보습"), List.of("수분 공급"), 1, false, false)));
        when(enrichmentService.getOrEnrich(anyList())).thenReturn(
                new ShortformProductEnrichmentService.BatchResult(
                        Map.of(1, enriched), "gpt-test", "2.0", 1, 1, 0, 1));

        Map<Long, InventoryProductEvidence> result = service.enrichMatchingCategories(
                List.of(new InventoryFact(100L, 20L, "보유 수분 앰플", "테스트", "ESSENCE_SERUM", null)),
                Set.of(ProductCategory.ESSENCE_SERUM));

        assertThat(result.get(20L).ingredients()).singleElement()
                .extracting(item -> item.name())
                .isEqualTo("히알루론산");
        verify(enrichmentService).getOrEnrich(anyList());
    }
}
