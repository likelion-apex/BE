package domain.ingredient.util;

import domain.inventory.ProductCategory;
import java.util.Map;

public final class ProductCategoryDisplay {

    private static final Map<ProductCategory, String> KOREAN_LABELS = Map.of(
            ProductCategory.SKIN_TONER, "스킨/토너",
            ProductCategory.LOTION, "로션/에멀전",
            ProductCategory.ESSENCE_SERUM, "에센스/앰플/세럼",
            ProductCategory.FACEOIL, "페이스오일",
            ProductCategory.CREAM, "크림",
            ProductCategory.EYECARE, "아이케어",
            ProductCategory.MIST, "미스트,젤",
            ProductCategory.SKIN_TONERPAD, "스킨/토너 패드",
            ProductCategory.BAM, "밤/멀티밤",
            ProductCategory.ETC, "기타"
    );

    private ProductCategoryDisplay() {
    }

    public static String toKorean(ProductCategory category) {
        return category != null ? KOREAN_LABELS.get(category) : null;
    }
}
