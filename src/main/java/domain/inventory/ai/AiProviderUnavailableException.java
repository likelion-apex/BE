package domain.inventory.ai;

/**
 * OpenAI가 할당량 소진·5xx·타임아웃 등으로 응답하지 못해 Gemini로 넘길 때 사용한다.
 */
public class AiProviderUnavailableException extends RuntimeException {

    public AiProviderUnavailableException(String message) {
        super(message);
    }

    public AiProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
