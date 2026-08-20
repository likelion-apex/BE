package domain.beauty.shortform.application;

import domain.inventory.CategoryImageResolver;
import domain.inventory.ProductCategory;
import global.util.PublicUrlResolver;
import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class ShortformProductImageResolver {

    private final CategoryImageResolver categoryImageResolver;
    private final PublicUrlResolver publicUrlResolver;

    public ShortformProductImageResolver(
            CategoryImageResolver categoryImageResolver,
            PublicUrlResolver publicUrlResolver
    ) {
        this.categoryImageResolver = categoryImageResolver;
        this.publicUrlResolver = publicUrlResolver;
    }

    public String resolve(ProductCategory category, String externalImageUrl) {
        String categoryImage = categoryImageResolver.resolve(category);
        if (categoryImage != null) {
            return publicUrlResolver.resolve(categoryImage);
        }
        return secureHttpsUrl(externalImageUrl);
    }

    String secureHttpsUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    ? uri.toString()
                    : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
