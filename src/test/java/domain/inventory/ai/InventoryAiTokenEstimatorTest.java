package domain.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InventoryAiTokenEstimatorTest {

    @Test
    void estimatesKoreanTextAsOneTokenPerTwoCharacters() {
        assertThat(InventoryAiTokenEstimator.estimate("제품명: 바닥 토너")).isEqualTo(5);
        assertThat(InventoryAiTokenEstimator.estimate("ab")).isEqualTo(1);
        assertThat(InventoryAiTokenEstimator.estimate((String) null)).isEqualTo(1);
        assertThat(InventoryAiTokenEstimator.estimate("시스템", "사용자")).isEqualTo(3);
    }
}
