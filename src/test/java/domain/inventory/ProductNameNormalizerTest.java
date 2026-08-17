package domain.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductNameNormalizerTest {

    @Test
    void treatsTrailingNumbersAndSpacesAsSameHangulProduct() {
        assertThat(ProductNameNormalizer.canonicalKey("바닥 토너"))
                .isEqualTo("바닥토너")
                .isEqualTo(ProductNameNormalizer.canonicalKey("바닥 토너 01"))
                .isEqualTo(ProductNameNormalizer.canonicalKey("바닥토너"));
    }

    @Test
    void stripsDigitsFromKoreanCosmeticNames() {
        assertThat(ProductNameNormalizer.canonicalKey("라운드랩 1025 독도 토너"))
                .isEqualTo("라운드랩독도토너");
    }

    @Test
    void fallsBackToLowercaseAlphanumericWhenNoHangul() {
        assertThat(ProductNameNormalizer.canonicalKey("CeraVe PM 01"))
                .isEqualTo("ceravepm01");
    }

    @Test
    void blankInputIsEmptyKey() {
        assertThat(ProductNameNormalizer.canonicalKey("  ")).isEmpty();
        assertThat(ProductNameNormalizer.canonicalKey(null)).isEmpty();
    }
}
