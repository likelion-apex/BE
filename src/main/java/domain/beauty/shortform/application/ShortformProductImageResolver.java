package domain.beauty.shortform.application;

import domain.inventory.CategoryImageResolver;
import domain.inventory.ProductCategory;
import global.util.PublicUrlResolver;
import org.springframework.stereotype.Component;

@Component
public class ShortformProductImageResolver {

    private static final String ETC_IMAGE_PATH = "/images/categories/etc.png";

    private final CategoryImageResolver categoryImageResolver;
    private final PublicUrlResolver publicUrlResolver;

    public ShortformProductImageResolver(
            CategoryImageResolver categoryImageResolver,
            PublicUrlResolver publicUrlResolver
    ) {
        this.categoryImageResolver = categoryImageResolver;
        this.publicUrlResolver = publicUrlResolver;
    }

    public String resolve(ProductCategory category) {
        String categoryImage = categoryImageResolver.resolve(category);
        return publicUrlResolver.resolve(categoryImage == null ? ETC_IMAGE_PATH : categoryImage);
    }
}
