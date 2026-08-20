package domain.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import domain.inventory.CategoryImageResolver;
import domain.inventory.Product;
import domain.inventory.ProductCategory;
import domain.inventory.ProductRepository;
import domain.inventory.cache.PopularProductCache;
import domain.inventory.client.OpenAiCategoryClassifier;
import global.util.PublicUrlResolver;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceFindOrCreateTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryImageResolver categoryImageResolver;
    @Mock
    private OpenAiCategoryClassifier categoryClassifier;
    @Mock
    private PopularProductCache popularProductCache;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
                productRepository, categoryImageResolver, categoryClassifier, popularProductCache,
                new PublicUrlResolver(""));
    }

    @Test
    void reusesProductWhenNormalizedHangulKeyMatches() {
        Product existing = Product.builder()
                .name("바닥 토너")
                .category(ProductCategory.SKIN_TONER)
                .build();
        when(productRepository.findByNormalizedName("바닥토너")).thenReturn(Optional.of(existing));

        Product found = productService.findOrCreate("바닥 토너 01");

        assertThat(found).isSameAs(existing);
        verify(productRepository, never()).save(any());
        verify(categoryImageResolver, never()).resolve(any());
    }
}
