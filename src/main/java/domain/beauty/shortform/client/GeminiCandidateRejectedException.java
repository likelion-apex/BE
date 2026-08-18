package domain.beauty.shortform.client;

public class GeminiCandidateRejectedException extends RuntimeException {

    public GeminiCandidateRejectedException(String message) {
        super(message);
    }

    public GeminiCandidateRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
