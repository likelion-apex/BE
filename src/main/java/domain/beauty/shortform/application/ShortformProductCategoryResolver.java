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
        if (containsAny(normalized, "선크림", "선스크린", "선로션", "선젤", "자외선차단", "sunscreen", "suncream")) {
            return ProductCategory.SUNCREAM;
        }
        if (containsAny(normalized, "클렌저", "클렌징", "세안", "페이스워시", "cleansing", "cleanser", "facewash")) {
            return ProductCategory.CLEANSER;
        }
        if (containsAny(normalized, "토너패드", "패드", "마스크", "팩", "sheetmask", "maskpack")) {
            return ProductCategory.MASK;
        }
        if (containsAny(normalized, "에센스", "essence")) {
            return ProductCategory.ESSENCE;
        }
        if (containsAny(normalized, "세럼", "앰플", "serum", "ampoule")) {
            return ProductCategory.SERUM;
        }
        if (containsAny(normalized, "로션", "에멀전", "에멀젼", "lotion", "emulsion")) {
            return ProductCategory.LOTION;
        }
        if (containsAny(normalized, "크림", "수분밤", "보습밤", "cream")) {
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
