package domain.inventory.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import domain.cosmetic.client.KakaoImageClient;
import domain.inventory.Product;
import domain.inventory.ProductCategory;
import domain.inventory.ProductRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class ProductFileSeederTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private KakaoImageClient kakaoImageClient;
    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    void insertsNewProductAndFillsImageFromKakaoWhenCsvUrlBlank() {
        stubTransaction();
        String csv = """
                name,brand,category,imageUrl
                라운드랩 1025 독도 토너,라운드랩,SKIN_TONER,
                """;
        ProductFileSeeder seeder = newSeeder(csv);
        when(productRepository.findByName("라운드랩 1025 독도 토너")).thenReturn(Optional.empty());
        when(kakaoImageClient.searchImageUrl("라운드랩 1025 독도 토너")).thenReturn("https://img.example/toner.jpg");

        seeder.seed();

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("라운드랩 1025 독도 토너");
        assertThat(saved.getBrand()).isEqualTo("라운드랩");
        assertThat(saved.getCategory()).isEqualTo(ProductCategory.SKIN_TONER);
        assertThat(saved.getImageUrl()).isEqualTo("https://img.example/toner.jpg");
    }

    @Test
    void updatesExistingProductWithoutCreatingDuplicate() {
        stubTransaction();
        String csv = """
                name,brand,category,imageUrl
                라운드랩 1025 독도 토너,라운드랩,SKIN_TONER,https://img.example/new.jpg
                """;
        Product existing = Product.builder()
                .name("라운드랩 1025 독도 토너")
                .brand("구브랜드")
                .category(ProductCategory.ETC)
                .imageUrl("https://img.example/old.jpg")
                .build();
        ReflectionTestUtils.setField(existing, "id", 1L);
        ProductFileSeeder seeder = newSeeder(csv);
        when(productRepository.findByName("라운드랩 1025 독도 토너")).thenReturn(Optional.of(existing));

        seeder.seed();

        verify(productRepository, never()).save(any());
        assertThat(existing.getBrand()).isEqualTo("라운드랩");
        assertThat(existing.getCategory()).isEqualTo(ProductCategory.SKIN_TONER);
        assertThat(existing.getImageUrl()).isEqualTo("https://img.example/new.jpg");
        verify(kakaoImageClient, never()).searchImageUrl(any());
    }

    @Test
    void skipsInvalidCategoryRows() {
        String csv = """
                name,brand,category,imageUrl
                잘못된 상품,브랜드,NOT_A_CATEGORY,
                """;
        ProductFileSeeder seeder = newSeeder(csv);

        seeder.seed();

        verify(productRepository, never()).findByName(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void reusesExistingImageWhenCsvUrlBlank() {
        stubTransaction();
        String csv = """
                name,brand,category,imageUrl
                라운드랩 1025 독도 토너,라운드랩,SKIN_TONER,
                """;
        Product existing = Product.builder()
                .name("라운드랩 1025 독도 토너")
                .brand("라운드랩")
                .category(ProductCategory.SKIN_TONER)
                .imageUrl("https://img.example/kept.jpg")
                .build();
        ProductFileSeeder seeder = newSeeder(csv);
        when(productRepository.findByName("라운드랩 1025 독도 토너")).thenReturn(Optional.of(existing));

        seeder.seed();

        verify(kakaoImageClient, never()).searchImageUrl(any());
        assertThat(existing.getImageUrl()).isEqualTo("https://img.example/kept.jpg");
        verify(transactionManager, times(1)).commit(any());
    }

    private void stubTransaction() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    private ProductFileSeeder newSeeder(String csv) {
        return new ProductFileSeeder(
                productRepository,
                kakaoImageClient,
                transactionManager,
                true,
                new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)),
                0
        );
    }
}
