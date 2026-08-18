package domain.inventory.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inventory")
public class InventoryAiProperties {

    private Duration aiCacheTtl = Duration.ofDays(30);
    private Duration connectTimeout = Duration.ofSeconds(1);
    private Duration readTimeout = Duration.ofSeconds(3);
    private int openaiMaxInputTokens = 600;
    private int openaiMaxOutputTokens = 400;

    public Duration getAiCacheTtl() {
        return aiCacheTtl;
    }

    public void setAiCacheTtl(Duration aiCacheTtl) {
        this.aiCacheTtl = aiCacheTtl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getOpenaiMaxInputTokens() {
        return openaiMaxInputTokens;
    }

    public void setOpenaiMaxInputTokens(int openaiMaxInputTokens) {
        this.openaiMaxInputTokens = openaiMaxInputTokens;
    }

    public int getOpenaiMaxOutputTokens() {
        return openaiMaxOutputTokens;
    }

    public void setOpenaiMaxOutputTokens(int openaiMaxOutputTokens) {
        this.openaiMaxOutputTokens = openaiMaxOutputTokens;
    }
}
