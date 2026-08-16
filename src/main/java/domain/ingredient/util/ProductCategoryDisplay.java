package domain.ingredient.util;

import domain.inventory.ProductCategory;
import java.util.Map;

public final class ProductCategoryDisplay {

    private static final Map<ProductCategory, String> KOREAN_LABELS = Map.of(
            ProductCategory.SKIN_TONER, "스킨/토너",
            ProductCategory.SERUM, "세럼/앰플",
            ProductCategory.CREAM, "크림",
            ProductCategory.ESSENCE, "에센스",
            ProductCategory.LOTION, "로션/에멀전",
            ProductCategory.SUNCREAM, "선케어",
            ProductCategory.CLEANSER, "클렌징",
            ProductCategory.MASK, "마스크/팩",
            ProductCategory.ETC, "기타"
    );

    private ProductCategoryDisplay() {
    }

    public static String toKorean(ProductCategory category) {
        return category != null ? KOREAN_LABELS.get(category) : null;
    }
}
