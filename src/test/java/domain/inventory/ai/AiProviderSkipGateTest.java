package domain.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiProviderSkipGateTest {

    private static InventoryAiProperties defaultProperties() {
        return new InventoryAiProperties();
    }

    @Test
    void nonQuotaFailureDoesNotTriggerCooldown() {
        AiProviderSkipGate gate = new AiProviderSkipGate(defaultProperties());
        assertThat(gate.shouldSkip(AiProvider.OPENAI)).isFalse();

        gate.markFrom(AiProvider.OPENAI, new AiProviderUnavailableException("timeout"));

        assertThat(gate.shouldSkip(AiProvider.OPENAI)).isFalse();
    }

    @Test
    void quotaExceededTriggersDefaultCooldown() {
        InventoryAiProperties properties = defaultProperties();
        assertThat(properties.getOpenaiQuotaCooldown()).isEqualTo(Duration.ofHours(1));

        AiProviderSkipGate gate = new AiProviderSkipGate(properties);
        gate.markFrom(AiProvider.OPENAI, AiProviderUnavailableException.quota("429", null));

        assertThat(gate.shouldSkip(AiProvider.OPENAI)).isTrue();
    }

    @Test
    void quotaExceededWithRetryAfterUsesServerProvidedDuration() {
        AiProviderSkipGate gate = new AiProviderSkipGate(defaultProperties());

        gate.markFrom(AiProvider.GROQ,
                AiProviderUnavailableException.quota("429", null, Duration.ofMillis(1)));
        assertThat(gate.shouldSkip(AiProvider.GROQ)).isTrue();

        // retryAfter가 1ms이므로 곧 만료되어야 한다(기본 1시간 쿨다운과 달리 매우 짧다).
        awaitUntilFalse(() -> gate.shouldSkip(AiProvider.GROQ));
        assertThat(gate.shouldSkip(AiProvider.GROQ)).isFalse();
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
        assertThat(gate.shouldSkip(AiProvider.GEMINI)).isFalse();
        assertThat(gate.shouldSkip(AiProvider.GROQ)).isFalse();
    }

    @Test
    void nullExceptionDoesNotTriggerCooldown() {
        AiProviderSkipGate gate = new AiProviderSkipGate(defaultProperties());

        gate.markFrom(AiProvider.OPENAI, null);

        assertThat(gate.shouldSkip(AiProvider.OPENAI)).isFalse();
    }

    private static void awaitUntilFalse(java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 1000;
        while (condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
