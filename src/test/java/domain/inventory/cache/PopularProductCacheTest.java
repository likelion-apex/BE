package domain.inventory.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import domain.inventory.InventoryRepository;
import domain.inventory.Product;
import domain.inventory.ProductCategory;
import domain.inventory.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PopularProductCacheTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private ProductRepository productRepository;

    private PopularProductCache cache;

    @BeforeEach
    void setUp() {
        cache = new PopularProductCache(inventoryRepository, productRepository, 100);
    }

    @Test
    void refreshLoadsTopProductsIntoMemory() {
        Product popular = Product.builder()
                .name("라운드랩 1025 독도 토너")
                .brand("라운드랩")
                .category(ProductCategory.SKIN_TONER)
                .build();
        ReflectionTestUtils.setField(popular, "id", 10L);
        when(inventoryRepository.findTopProductIdsByInventoryCount(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(10L));
        when(productRepository.findAllById(List.of(10L))).thenReturn(List.of(popular));

        cache.refresh();

        assertThat(cache.find(10L)).contains(popular);
        assertThat(cache.find(99L)).isEmpty();
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(inventoryRepository).findTopProductIdsByInventoryCount(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }
}
