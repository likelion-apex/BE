package domain.beauty.shortform.client;

public class GeminiModelRoutingException extends RuntimeException {

    private final boolean configurationFailure;

    public GeminiModelRoutingException(String message, Throwable cause, boolean configurationFailure) {
        super(message, cause);
        this.configurationFailure = configurationFailure;
    }

    public boolean isConfigurationFailure() {
        return configurationFailure;
    }
}
