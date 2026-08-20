package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;

import domain.inventory.CategoryImageResolver;
import domain.inventory.ProductCategory;
import global.util.PublicUrlResolver;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ShortformProductImageResolverTest {

    private final ShortformProductImageResolver resolver = new ShortformProductImageResolver(
            new CategoryImageResolver(), new PublicUrlResolver("https://mutsa.dev.me.kr"));

    @Test
    void returnsAbsoluteStaticImageForEverySupportedCategory() {
        for (ProductCategory category : ProductCategory.values()) {
            assertThat(resolver.resolve(category))
                    .isEqualTo("https://mutsa.dev.me.kr/images/categories/"
                            + category.name().toLowerCase() + ".png");
        }
    }

    @Test
    void returnsEtcImageForUnsupportedCategories() {
        assertThat(resolver.resolve(null))
                .isEqualTo("https://mutsa.dev.me.kr/images/categories/etc.png");
        assertThat(new ClassPathResource("static/images/categories/etc.png").exists()).isTrue();
    }
}
