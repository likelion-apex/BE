package domain.inventory.ai;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * provider(OpenAI/Gemini/Groq)별로 할당량 소진(429) 이후 같은 요청에서 다시 기다리지 않도록
 * 독립적인 쿨다운을 관리한다. 429 응답이 {@code Retry-After}/{@code retryDelay}로 대기 시간을
 * 알려주면 그 값을 그대로 쓰고, 없으면 provider별 기본값(기본 1시간)을 사용한다. 타임아웃·5xx·
 * 연결 실패 등 할당량 소진이 아닌 실패는 일시적 콘텐츠/네트워크 문제로 보고 쿨다운을 걸지 않는다
 * (이번 요청 안에서는 이미 다음 provider로 폴백했고, 다음 요청은 다시 처음부터 시도한다).
 */
@Component
public class AiProviderSkipGate {

    private final Map<AiProvider, AtomicLong> skipUntilEpochMsByProvider = new EnumMap<>(AiProvider.class);
    private final Map<AiProvider, Duration> defaultQuotaCooldownByProvider = new EnumMap<>(AiProvider.class);

    public AiProviderSkipGate(InventoryAiProperties properties) {
        defaultQuotaCooldownByProvider.put(AiProvider.OPENAI, properties.getOpenaiQuotaCooldown());
        defaultQuotaCooldownByProvider.put(AiProvider.GEMINI, properties.getGeminiQuotaCooldown());
        defaultQuotaCooldownByProvider.put(AiProvider.GROQ, properties.getGroqQuotaCooldown());
        for (AiProvider provider : AiProvider.values()) {
            skipUntilEpochMsByProvider.put(provider, new AtomicLong(0));
        }
    }

    public boolean shouldSkip(AiProvider provider) {
        return System.currentTimeMillis() < skipUntilEpochMsByProvider.get(provider).get();
    }

    public void markFrom(AiProvider provider, AiProviderUnavailableException exception) {
        if (exception == null || !exception.isQuotaExceeded()) {
            return;
        }
        Duration cooldown = exception.getRetryAfter() != null
                ? exception.getRetryAfter()
                : defaultQuotaCooldownByProvider.get(provider);
        skipFor(provider, cooldown);
    }

    private void skipFor(AiProvider provider, Duration cooldown) {
        skipUntilEpochMsByProvider.get(provider).set(System.currentTimeMillis() + cooldown.toMillis());
    }
}
