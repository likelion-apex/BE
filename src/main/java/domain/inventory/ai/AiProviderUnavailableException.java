package domain.inventory.ai;

import java.time.Duration;

/**
 * OpenAI가 할당량 소진·5xx·타임아웃 등으로 응답하지 못해 폴백할 때 사용한다.
 */
public class AiProviderUnavailableException extends RuntimeException {

    private final boolean quotaExceeded;
    private final Duration retryAfter;

    public AiProviderUnavailableException(String message) {
        this(message, null, false, null);
    }

    public AiProviderUnavailableException(String message, Throwable cause) {
        this(message, cause, false, null);
    }

    private AiProviderUnavailableException(String message, Throwable cause, boolean quotaExceeded, Duration retryAfter) {
        super(message, cause);
        this.quotaExceeded = quotaExceeded;
        this.retryAfter = retryAfter;
    }

    public static AiProviderUnavailableException quota(String message, Throwable cause) {
        return new AiProviderUnavailableException(message, cause, true, null);
    }

    /**
     * 429 응답이 {@code Retry-After} 헤더나 바디에 실제 대기 시간을 알려준 경우 사용한다.
     * {@link AiProviderSkipGate}가 이 값이 있으면 provider별 고정 쿨다운 대신 이 값을 사용한다.
     */
    public static AiProviderUnavailableException quota(String message, Throwable cause, Duration retryAfter) {
        return new AiProviderUnavailableException(message, cause, true, retryAfter);
    }

    public boolean isQuotaExceeded() {
        return quotaExceeded;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
