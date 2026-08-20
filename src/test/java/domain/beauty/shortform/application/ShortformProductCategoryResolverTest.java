package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;

import domain.inventory.ProductCategory;
import org.junit.jupiter.api.Test;

class ShortformProductCategoryResolverTest {

    private final ShortformProductCategoryResolver resolver = new ShortformProductCategoryResolver();

    @Test
    void mapsVideoLabelsToStoredProductCategories() {
        assertThat(resolver.resolve("앰플", "수분 앰플")).isEqualTo(ProductCategory.ESSENCE_SERUM);
        assertThat(resolver.resolve("토너", "진정 토너")).isEqualTo(ProductCategory.SKIN_TONER);
        assertThat(resolver.resolve("토너 패드", "진정 패드")).isEqualTo(ProductCategory.SKIN_TONERPAD);
        assertThat(resolver.resolve("수딩 크림", null)).isEqualTo(ProductCategory.CREAM);
    }

    @Test
    void leavesUnknownCategoryAsNull() {
        assertThat(resolver.resolve("도포 제품", "확인되지 않은 제품")).isNull();
        assertThat(resolver.parseStored("UNKNOWN")).isNull();
    }

    @Test
    void doesNotMisclassifyUnsupportedCleansersByOilOrGelKeyword() {
        assertThat(resolver.resolve("오일", "퓨어 클렌징 오일")).isNull();
        assertThat(resolver.resolve("젤", "약산성 젤 클렌저")).isNull();
        assertThat(resolver.resolve("선케어", "수분 선크림")).isNull();
        assertThat(resolver.resolve("팩", "진정 마스크팩")).isNull();
    }
}
