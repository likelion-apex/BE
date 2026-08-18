package domain.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenAiSkipGateTest {

    @Test
    void skipsAfterTimeoutCooldown() {
        OpenAiSkipGate gate = new OpenAiSkipGate();
        assertThat(gate.shouldSkip()).isFalse();
        gate.markTimeout();
        assertThat(gate.shouldSkip()).isTrue();
    }

    @Test
    void usesQuotaCooldownFor429() {
        OpenAiSkipGate gate = new OpenAiSkipGate();
        gate.markFrom(AiProviderUnavailableException.quota("429", null));
        assertThat(gate.shouldSkip()).isTrue();
        assertThat(OpenAiSkipGate.QUOTA_COOLDOWN.toHours()).isEqualTo(1);
        assertThat(OpenAiSkipGate.TIMEOUT_COOLDOWN.toMinutes()).isEqualTo(2);
    }
}
