package domain.inventory.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inventory")
public class InventoryAiProperties {

    private Duration aiCacheTtl = Duration.ofDays(30);
    private Duration connectTimeout = Duration.ofSeconds(1);
    private Duration readTimeout = Duration.ofSeconds(6);
    private Duration geminiConnectTimeout = Duration.ofSeconds(3);
    private Duration geminiReadTimeout = Duration.ofSeconds(5);
    private Duration groqConnectTimeout = Duration.ofSeconds(1);
    private Duration groqReadTimeout = Duration.ofSeconds(2);
    private int openaiMaxOutputTokens = 400;

    private Duration openaiQuotaCooldown = Duration.ofHours(1);
    private Duration openaiTimeoutCooldown = Duration.ofMinutes(2);
    private Duration geminiQuotaCooldown = Duration.ofHours(1);
    private Duration geminiTimeoutCooldown = Duration.ofMinutes(2);
    private Duration groqQuotaCooldown = Duration.ofHours(1);
    private Duration groqTimeoutCooldown = Duration.ofMinutes(2);

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

    public Duration getGeminiConnectTimeout() {
        return geminiConnectTimeout;
    }

    public void setGeminiConnectTimeout(Duration geminiConnectTimeout) {
        this.geminiConnectTimeout = geminiConnectTimeout;
    }

    public Duration getGeminiReadTimeout() {
        return geminiReadTimeout;
    }

    public void setGeminiReadTimeout(Duration geminiReadTimeout) {
        this.geminiReadTimeout = geminiReadTimeout;
    }

    public Duration getGroqConnectTimeout() {
        return groqConnectTimeout;
    }

    public void setGroqConnectTimeout(Duration groqConnectTimeout) {
        this.groqConnectTimeout = groqConnectTimeout;
    }

    public Duration getGroqReadTimeout() {
        return groqReadTimeout;
    }

    public void setGroqReadTimeout(Duration groqReadTimeout) {
        this.groqReadTimeout = groqReadTimeout;
    }

    public int getOpenaiMaxOutputTokens() {
        return openaiMaxOutputTokens;
    }

    public void setOpenaiMaxOutputTokens(int openaiMaxOutputTokens) {
        this.openaiMaxOutputTokens = openaiMaxOutputTokens;
    }

    public Duration getOpenaiQuotaCooldown() {
        return openaiQuotaCooldown;
    }

    public void setOpenaiQuotaCooldown(Duration openaiQuotaCooldown) {
        this.openaiQuotaCooldown = openaiQuotaCooldown;
    }

    public Duration getOpenaiTimeoutCooldown() {
        return openaiTimeoutCooldown;
    }

    public void setOpenaiTimeoutCooldown(Duration openaiTimeoutCooldown) {
        this.openaiTimeoutCooldown = openaiTimeoutCooldown;
    }

    public Duration getGeminiQuotaCooldown() {
        return geminiQuotaCooldown;
    }

    public void setGeminiQuotaCooldown(Duration geminiQuotaCooldown) {
        this.geminiQuotaCooldown = geminiQuotaCooldown;
    }

    public Duration getGeminiTimeoutCooldown() {
        return geminiTimeoutCooldown;
    }

    public void setGeminiTimeoutCooldown(Duration geminiTimeoutCooldown) {
        this.geminiTimeoutCooldown = geminiTimeoutCooldown;
    }

    public Duration getGroqQuotaCooldown() {
        return groqQuotaCooldown;
    }

    public void setGroqQuotaCooldown(Duration groqQuotaCooldown) {
        this.groqQuotaCooldown = groqQuotaCooldown;
    }

    public Duration getGroqTimeoutCooldown() {
        return groqTimeoutCooldown;
    }

    public void setGroqTimeoutCooldown(Duration groqTimeoutCooldown) {
        this.groqTimeoutCooldown = groqTimeoutCooldown;
    }
}
