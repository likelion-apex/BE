package domain.beauty.shortform.application;

import domain.inventory.ProductCategory;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ShortformProductCategoryResolver {

    public ProductCategory resolve(String category, String productName) {
        ProductCategory resolved = resolveValue(category);
        return resolved != ProductCategory.ETC ? resolved : resolveValue(productName);
    }

    public ProductCategory parseStored(String category) {
        if (category == null || category.isBlank()) {
            return ProductCategory.ETC;
        }
        try {
            return ProductCategory.valueOf(category.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ProductCategory.ETC;
        }
    }

    private ProductCategory resolveValue(String value) {
        if (value == null || value.isBlank()) {
            return ProductCategory.ETC;
        }

        ProductCategory stored = parseStored(value);
        if (stored != ProductCategory.ETC || "ETC".equalsIgnoreCase(value.trim())) {
            return stored;
        }

        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[\\s_/-]", "");
        if (containsAny(normalized, "아이크림", "아이세럼", "아이케어", "eyecream", "eyeserum", "eyecare")) {
            return ProductCategory.EYECARE;
        }
        if (containsAny(normalized, "페이스오일", "오일", "faceoil")) {
            return ProductCategory.FACEOIL;
        }
        if (containsAny(normalized, "미스트", "젤", "mist")) {
            return ProductCategory.MIST;
        }
        if (containsAny(normalized, "토너패드", "패드")) {
            return ProductCategory.SKIN_TONERPAD;
        }
        if (containsAny(normalized, "밤", "멀티밤", "balm")) {
            return ProductCategory.BAM;
        }
        if (containsAny(normalized, "에센스", "세럼", "앰플", "essence", "serum", "ampoule")) {
            return ProductCategory.ESSENCE_SERUM;
        }
        if (containsAny(normalized, "로션", "에멀전", "에멀젼", "lotion", "emulsion")) {
            return ProductCategory.LOTION;
        }
        if (containsAny(normalized, "크림", "cream")) {
            return ProductCategory.CREAM;
        }
        if (containsAny(normalized, "토너", "스킨", "toner")) {
            return ProductCategory.SKIN_TONER;
        }
        return ProductCategory.ETC;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
