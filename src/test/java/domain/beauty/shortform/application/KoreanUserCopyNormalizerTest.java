package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;

import domain.inventory.ProductCategory;
import java.util.List;
import org.junit.jupiter.api.Test;

class KoreanUserCopyNormalizerTest {

    private final KoreanUserCopyNormalizer normalizer = new KoreanUserCopyNormalizer();

    @Test
    void rejectsEnglishOnlyAndLongInciListsButAllowsCommonShortTokens() {
        assertThat(normalizer.isAcceptable("Lavandula Angustifolia Oil, Citrus Aurantium Dulcis Oil은 주의가 필요해요."))
                .isFalse();
        assertThat(normalizer.isAcceptable("Collagen Water와 Hydrolyzed Hyaluronic Acid가 수분을 공급해요."))
                .isFalse();
        assertThat(normalizer.isAcceptable("PDRN과 보습 성분이 피부 컨디션 관리에 도움을 줄 수 있어요."))
                .isTrue();
        assertThat(normalizer.isAcceptable("pH 조절 성분이 포함되어 적은 양부터 사용해 주세요."))
                .isTrue();
    }

    @Test
    void replacesInvalidBenefitsWithDeterministicKoreanCategoryCopy() {
        assertThat(normalizer.normalizeBenefits(
                List.of("Hydrating and Soothing"), "앰플", "수분 앰플",
                new ShortformProductCategoryResolver()))
                .containsExactly("수분 공급");
    }

    @Test
    void usesGenericCopyForEtcCategory() {
        ShortformProductCategoryResolver resolver = new ShortformProductCategoryResolver();
        assertThat(normalizer.normalizeBenefits(
                List.of("Hydrating"), "ETC", "기타 제품", resolver))
                .containsExactly("피부 컨디션 관리");
        assertThat(normalizer.optimizationFallback(ProductCategory.ETC, false))
                .isEqualTo("해당 단계의 피부 관리 역할을 대신할 확인된 보유 제품이 없어 영상 속 제품을 사용해 주세요.");
    }
}
