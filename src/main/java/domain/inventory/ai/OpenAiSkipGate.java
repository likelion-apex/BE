package domain.inventory.ai;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * OpenAI 할당량·타임아웃 이후 같은 요청에서 다시 기다리지 않도록 건너뛴다.
 * 429(일일 한도)는 1시간, 그 외 장애는 2분.
 */
@Component
public class OpenAiSkipGate {

    static final Duration QUOTA_COOLDOWN = Duration.ofHours(1);
    static final Duration TIMEOUT_COOLDOWN = Duration.ofMinutes(2);

    private final AtomicLong skipUntilEpochMs = new AtomicLong(0);

    public boolean shouldSkip() {
        return System.currentTimeMillis() < skipUntilEpochMs.get();
    }

    public void markUnavailable() {
        markTimeout();
    }

    public void markTimeout() {
        skipFor(TIMEOUT_COOLDOWN);
    }

    public void markQuotaExceeded() {
        skipFor(QUOTA_COOLDOWN);
    }

    public void markFrom(AiProviderUnavailableException exception) {
        if (exception != null && exception.isQuotaExceeded()) {
            markQuotaExceeded();
            return;
        }
        markTimeout();
    }

    private void skipFor(Duration cooldown) {
        skipUntilEpochMs.set(System.currentTimeMillis() + cooldown.toMillis());
    }
}
