package domain.ingredient.util;

import static org.assertj.core.api.Assertions.assertThat;

import domain.inventory.ProductCategory;
import org.junit.jupiter.api.Test;

class ProductCategoryDisplayTest {

    @Test
    void toKoreanReturnsEtcLabel() {
        assertThat(ProductCategoryDisplay.toKorean(ProductCategory.ETC)).isEqualTo("기타");
    }

    @Test
    void toKoreanReturnsNullWhenCategoryIsNull() {
        assertThat(ProductCategoryDisplay.toKorean(null)).isNull();
    }
}
