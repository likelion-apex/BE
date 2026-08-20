package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;

import domain.inventory.CategoryImageResolver;
import domain.inventory.ProductCategory;
import global.util.PublicUrlResolver;
import org.junit.jupiter.api.Test;

class ShortformProductImageResolverTest {

    private final ShortformProductImageResolver resolver = new ShortformProductImageResolver(
            new CategoryImageResolver(), new PublicUrlResolver("https://mutsa.dev.me.kr"));

    @Test
    void returnsAbsoluteStaticImageForEverySupportedCategory() {
        for (ProductCategory category : ProductCategory.values()) {
            assertThat(resolver.resolve(category, "https://external.example/product.jpg"))
                    .isEqualTo("https://mutsa.dev.me.kr/images/categories/"
                            + category.name().toLowerCase() + ".png");
        }
    }

    @Test
    void allowsOnlyValidHttpsExternalImagesForUnsupportedCategories() {
        assertThat(resolver.resolve(null, "https://images.example/product.jpg"))
                .isEqualTo("https://images.example/product.jpg");
        assertThat(resolver.resolve(null, "http://images.example/product.jpg")).isNull();
        assertThat(resolver.resolve(null, "not-a-url")).isNull();
    }
}
