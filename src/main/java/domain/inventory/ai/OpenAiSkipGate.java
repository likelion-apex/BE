package domain.inventory.ai;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * OpenAI 할당량·타임아웃 이후 같은 요청 체인에서 20초씩 재시도하지 않도록 짧게 건너뛴다.
 */
@Component
public class OpenAiSkipGate {

    private static final Duration COOLDOWN = Duration.ofMinutes(10);
    private final AtomicLong skipUntilEpochMs = new AtomicLong(0);

    public boolean shouldSkip() {
        return System.currentTimeMillis() < skipUntilEpochMs.get();
    }

    public void markUnavailable() {
        skipUntilEpochMs.set(System.currentTimeMillis() + COOLDOWN.toMillis());
    }
}
