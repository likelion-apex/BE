package domain.inventory.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkinEfficacyTagTest {

    @Test
    void fromRaw_mapsKnownEnumCodes() {
        assertThat(SkinEfficacyTag.fromRaw("MOISTURIZING")).isEqualTo(SkinEfficacyTag.MOISTURIZING);
        assertThat(SkinEfficacyTag.fromRaw("protection")).isEqualTo(SkinEfficacyTag.PROTECTION);
    }

    @Test
    void fromRaw_mapsKoreanLabels() {
        assertThat(SkinEfficacyTag.fromRaw("피부 보습")).isEqualTo(SkinEfficacyTag.MOISTURIZING);
        assertThat(SkinEfficacyTag.fromRaw("피부 보호")).isEqualTo(SkinEfficacyTag.PROTECTION);
    }

    @Test
    void fromRaw_returnsNullWhenNullOrBlankOrUnmatched() {
        assertThat(SkinEfficacyTag.fromRaw(null)).isNull();
        assertThat(SkinEfficacyTag.fromRaw("")).isNull();
        assertThat(SkinEfficacyTag.fromRaw("   ")).isNull();
        assertThat(SkinEfficacyTag.fromRaw("피부 진정")).isNull();
    }

    @Test
    void getLabel_returnsKoreanDisplayText() {
        assertThat(SkinEfficacyTag.MOISTURIZING.getLabel()).isEqualTo("피부 보습");
        assertThat(SkinEfficacyTag.PROTECTION.getLabel()).isEqualTo("피부 보호");
    }
}
