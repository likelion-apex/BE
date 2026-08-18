package domain.inventory.ai;

/**
 * OpenAI가 할당량 소진·5xx·타임아웃 등으로 응답하지 못해 폴백할 때 사용한다.
 */
public class AiProviderUnavailableException extends RuntimeException {

    private final boolean quotaExceeded;

    public AiProviderUnavailableException(String message) {
        this(message, null, false);
    }

    public AiProviderUnavailableException(String message, Throwable cause) {
        this(message, cause, false);
    }

    private AiProviderUnavailableException(String message, Throwable cause, boolean quotaExceeded) {
        super(message, cause);
        this.quotaExceeded = quotaExceeded;
    }

    public static AiProviderUnavailableException quota(String message, Throwable cause) {
        return new AiProviderUnavailableException(message, cause, true);
    }

    public boolean isQuotaExceeded() {
        return quotaExceeded;
    }
}
