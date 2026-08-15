package domain.beauty.shortform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shortform.product-enrichment")
public class ShortformProductEnrichmentProperties {

    private boolean cacheEnabled = true;
    private boolean geminiFallbackEnabled = true;
    private String geminiPromptVersion = "1.1";
    private int geminiMaxOutputTokens = 12_000;
    private double estimatedResolutionThreshold = 0.60;

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public boolean isGeminiFallbackEnabled() {
        return geminiFallbackEnabled;
    }

    public void setGeminiFallbackEnabled(boolean geminiFallbackEnabled) {
        this.geminiFallbackEnabled = geminiFallbackEnabled;
    }

    public String getGeminiPromptVersion() {
        return geminiPromptVersion;
    }

    public void setGeminiPromptVersion(String geminiPromptVersion) {
        this.geminiPromptVersion = geminiPromptVersion;
    }

    public int getGeminiMaxOutputTokens() {
        return geminiMaxOutputTokens;
    }

    public void setGeminiMaxOutputTokens(int geminiMaxOutputTokens) {
        this.geminiMaxOutputTokens = geminiMaxOutputTokens;
    }

    public double getEstimatedResolutionThreshold() {
        return estimatedResolutionThreshold;
    }

    public void setEstimatedResolutionThreshold(double estimatedResolutionThreshold) {
        this.estimatedResolutionThreshold = estimatedResolutionThreshold;
    }
}
