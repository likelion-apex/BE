package domain.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiProviderSkipGateTest {

    private static InventoryAiProperties defaultProperties() {
        return new InventoryAiProperties();
    }

    @Test
    void skipsAfterTimeoutCooldown() {
        AiProviderSkipGate gate = new AiProviderSkipGate(defaultProperties());
        assertThat(gate.shouldSkip(AiProvider.OPENAI)).isFalse();
        gate.markFrom(AiProvider.OPENAI, new AiProviderUnavailableException("timeout"));
        assertThat(gate.shouldSkip(AiProvider.OPENAI)).isTrue();
    }

    @Test
    void usesQuotaCooldownFor429() {
        InventoryAiProperties properties = defaultProperties();
        assertThat(properties.getOpenaiQuotaCooldown()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.getOpenaiTimeoutCooldown()).isEqualTo(Duration.ofMinutes(2));

        AiProviderSkipGate gate = new AiProviderSkipGate(properties);
        gate.markFrom(AiProvider.OPENAI, AiProviderUnavailableException.quota("429", null));
        assertThat(gate.shouldSkip(AiProvider.OPENAI)).isTrue();
    }

    @Test
    void cooldownsAreIndependentPerProvider() {
        AiProviderSkipGate gate = new AiProviderSkipGate(defaultProperties());

        gate.markFrom(AiProvider.OPENAI, AiProviderUnavailableException.quota("429", null));

        assertThat(gate.shouldSkip(AiProvider.OPENAI)).isTrue();
        assertThat(gate.shouldSkip(AiProvider.GEMINI)).isFalse();
        assertThat(gate.shouldSkip(AiProvider.GROQ)).isFalse();

        gate.markFrom(AiProvider.GEMINI, new AiProviderUnavailableException("timeout"));

        assertThat(gate.shouldSkip(AiProvider.OPENAI)).isTrue();
        assertThat(gate.shouldSkip(AiProvider.GEMINI)).isTrue();
        assertThat(gate.shouldSkip(AiProvider.GROQ)).isFalse();
    }

    @Test
    void respectsPerProviderCooldownOverrides() {
        InventoryAiProperties properties = defaultProperties();
        properties.setGroqTimeoutCooldown(Duration.ZERO);
        AiProviderSkipGate gate = new AiProviderSkipGate(properties);

        gate.markFrom(AiProvider.GROQ, new AiProviderUnavailableException("timeout"));

        assertThat(gate.shouldSkip(AiProvider.GROQ)).isFalse();
    }
}
