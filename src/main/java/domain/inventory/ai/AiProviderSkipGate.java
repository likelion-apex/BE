package domain.inventory.ai;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * provider(OpenAI/Gemini/Groq)별로 할당량 소진·타임아웃 이후 같은 요청에서 다시 기다리지 않도록
 * 독립적인 쿨다운을 관리한다. 429(할당량)는 기본 1시간, 그 외 장애는 기본 2분이며 provider별로 조정 가능하다.
 */
@Component
public class AiProviderSkipGate {

    private record Cooldowns(Duration quota, Duration timeout) {}

    private final Map<AiProvider, AtomicLong> skipUntilEpochMsByProvider = new EnumMap<>(AiProvider.class);
    private final Map<AiProvider, Cooldowns> cooldownsByProvider = new EnumMap<>(AiProvider.class);

    public AiProviderSkipGate(InventoryAiProperties properties) {
        cooldownsByProvider.put(
                AiProvider.OPENAI,
                new Cooldowns(properties.getOpenaiQuotaCooldown(), properties.getOpenaiTimeoutCooldown()));
        cooldownsByProvider.put(
                AiProvider.GEMINI,
                new Cooldowns(properties.getGeminiQuotaCooldown(), properties.getGeminiTimeoutCooldown()));
        cooldownsByProvider.put(
                AiProvider.GROQ,
                new Cooldowns(properties.getGroqQuotaCooldown(), properties.getGroqTimeoutCooldown()));
        for (AiProvider provider : AiProvider.values()) {
            skipUntilEpochMsByProvider.put(provider, new AtomicLong(0));
        }
    }

    public boolean shouldSkip(AiProvider provider) {
        return System.currentTimeMillis() < skipUntilEpochMsByProvider.get(provider).get();
    }

    public void markFrom(AiProvider provider, AiProviderUnavailableException exception) {
        Cooldowns cooldowns = cooldownsByProvider.get(provider);
        Duration cooldown = (exception != null && exception.isQuotaExceeded())
                ? cooldowns.quota()
                : cooldowns.timeout();
        skipFor(provider, cooldown);
    }

    private void skipFor(AiProvider provider, Duration cooldown) {
        skipUntilEpochMsByProvider.get(provider).set(System.currentTimeMillis() + cooldown.toMillis());
    }
}
