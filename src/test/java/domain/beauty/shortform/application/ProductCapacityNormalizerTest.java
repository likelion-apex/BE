package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductCapacityNormalizerTest {

    private final ProductCapacityNormalizer normalizer = new ProductCapacityNormalizer();

    @Test
    void extractsOnlyNormalizedCapacityFromMarketCopy() {
        assertThat(normalizer.normalize("한국 / 100ml")).isEqualTo("100ml");
        assertThat(normalizer.normalize("한국 판매 제품, 200mL")).isEqualTo("200ml");
        assertThat(normalizer.normalize("국내 판매 제품 50 g")).isEqualTo("50g");
        assertThat(normalizer.normalize("1ℓ 리필")).isEqualTo("1L");
    }

    @Test
    void usesProductNameWhenEnrichmentCopyHasNoCapacity() {
        assertThat(normalizer.normalize(
                "한국 판매 처방", "리얼 히알루로닉 블루 100 앰플 100ml"))
                .isEqualTo("100ml");
    }

    @Test
    void returnsNullWhenCapacityIsMissingOrConflicts() {
        assertThat(normalizer.normalize("한국 판매 처방", "수분 앰플")).isNull();
        assertThat(normalizer.normalize("100ml", "수분 앰플 200ml")).isNull();
        assertThat(normalizer.normalize("50ml + 10ml 기획 세트")).isNull();
    }
}
