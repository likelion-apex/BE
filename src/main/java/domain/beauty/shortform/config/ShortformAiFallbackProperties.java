package domain.beauty.shortform.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shortform.ai-fallback")
public class ShortformAiFallbackProperties {

    private boolean geminiEnabled = true;
    private int geminiMaxOutputTokens = 6_000;
    private List<String> geminiVideoModels = List.of(
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-3-flash-preview");
    private List<String> geminiTextModels = List.of(
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-3.5-flash",
            "gemini-3.6-flash",
            "gemini-3-flash-preview");
    private List<String> geminiProductModels = List.of(
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-3.6-flash",
            "gemini-3-flash-preview");
    private Duration geminiMaxRetryDelay = Duration.ofSeconds(60);
    private Duration geminiDefaultRateLimitDelay = Duration.ofSeconds(30);
    private Duration geminiDefaultTransientDelay = Duration.ofSeconds(2);

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

    public List<String> getGeminiVideoModels() {
        return geminiVideoModels;
    }

    public void setGeminiVideoModels(List<String> geminiVideoModels) {
        this.geminiVideoModels = geminiVideoModels;
    }

    public List<String> getGeminiTextModels() {
        return geminiTextModels;
    }

    public void setGeminiTextModels(List<String> geminiTextModels) {
        this.geminiTextModels = geminiTextModels;
    }

    public List<String> getGeminiProductModels() {
        return geminiProductModels;
    }

    public void setGeminiProductModels(List<String> geminiProductModels) {
        this.geminiProductModels = geminiProductModels;
    }

    public Duration getGeminiMaxRetryDelay() {
        return geminiMaxRetryDelay;
    }

    public void setGeminiMaxRetryDelay(Duration geminiMaxRetryDelay) {
        this.geminiMaxRetryDelay = geminiMaxRetryDelay;
    }

    public Duration getGeminiDefaultRateLimitDelay() {
        return geminiDefaultRateLimitDelay;
    }

    public void setGeminiDefaultRateLimitDelay(Duration geminiDefaultRateLimitDelay) {
        this.geminiDefaultRateLimitDelay = geminiDefaultRateLimitDelay;
    }

    public Duration getGeminiDefaultTransientDelay() {
        return geminiDefaultTransientDelay;
    }

    public void setGeminiDefaultTransientDelay(Duration geminiDefaultTransientDelay) {
        this.geminiDefaultTransientDelay = geminiDefaultTransientDelay;
    }
}
