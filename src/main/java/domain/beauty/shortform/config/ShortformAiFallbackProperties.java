package domain.beauty.shortform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shortform.ai-fallback")
public class ShortformAiFallbackProperties {

    private boolean geminiEnabled = true;
    private int geminiMaxOutputTokens = 6_000;
    private int geminiMaxAttempts = 2;

    public boolean isGeminiEnabled() {
        return geminiEnabled;
    }

    public void setGeminiEnabled(boolean geminiEnabled) {
        this.geminiEnabled = geminiEnabled;
    }

    public int getGeminiMaxOutputTokens() {
        return geminiMaxOutputTokens;
    }

    public void setGeminiMaxOutputTokens(int geminiMaxOutputTokens) {
        this.geminiMaxOutputTokens = geminiMaxOutputTokens;
    }

    public int getGeminiMaxAttempts() {
        return geminiMaxAttempts;
    }

    public void setGeminiMaxAttempts(int geminiMaxAttempts) {
        this.geminiMaxAttempts = geminiMaxAttempts;
    }
}
