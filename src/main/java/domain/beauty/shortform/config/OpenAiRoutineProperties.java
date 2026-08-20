package domain.beauty.shortform.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public class OpenAiRoutineProperties {

    private String apiKey = "";
    private URI apiUrl = URI.create("https://api.openai.com/v1/chat/completions");
    private URI productApiUrl = URI.create("https://api.openai.com/v1/responses");
    private String organizationId = "";
    private String routineModel = "gpt-4o-mini";
    private String routinePromptVersion = "3.6";
    private String productModel = "gpt-5.6-luna";
    private String productFallbackModel = "gpt-5.4-mini";
    private String productReasoningEffort = "low";
    private String productPromptVersion = "2.0";
    private boolean productFallbackEnabled = true;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(120);
    private Duration productCacheTtl = Duration.ofDays(30);
    private Duration productNegativeCacheTtl = Duration.ofDays(1);
    private int maxOutputTokens = 6_000;
    private int productMaxOutputTokens = 12_000;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public URI getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(URI apiUrl) {
        this.apiUrl = apiUrl;
    }

    public URI getProductApiUrl() {
        return productApiUrl;
    }

    public void setProductApiUrl(URI productApiUrl) {
        this.productApiUrl = productApiUrl;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getRoutineModel() {
        return routineModel;
    }

    public void setRoutineModel(String routineModel) {
        this.routineModel = routineModel;
    }

    public String getRoutinePromptVersion() {
        return routinePromptVersion;
    }

    public void setRoutinePromptVersion(String routinePromptVersion) {
        this.routinePromptVersion = routinePromptVersion;
    }

    public String getProductModel() {
        return productModel;
    }

    public void setProductModel(String productModel) {
        this.productModel = productModel;
    }

    public String getProductFallbackModel() {
        return productFallbackModel;
    }

    public void setProductFallbackModel(String productFallbackModel) {
        this.productFallbackModel = productFallbackModel;
    }

    public String getProductReasoningEffort() {
        return productReasoningEffort;
    }

    public void setProductReasoningEffort(String productReasoningEffort) {
        this.productReasoningEffort = productReasoningEffort;
    }

    public String getProductPromptVersion() {
        return productPromptVersion;
    }

    public void setProductPromptVersion(String productPromptVersion) {
        this.productPromptVersion = productPromptVersion;
    }

    public boolean isProductFallbackEnabled() {
        return productFallbackEnabled;
    }

    public void setProductFallbackEnabled(boolean productFallbackEnabled) {
        this.productFallbackEnabled = productFallbackEnabled;
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

    public Duration getProductCacheTtl() {
        return productCacheTtl;
    }

    public void setProductCacheTtl(Duration productCacheTtl) {
        this.productCacheTtl = productCacheTtl;
    }

    public Duration getProductNegativeCacheTtl() {
        return productNegativeCacheTtl;
    }

    public void setProductNegativeCacheTtl(Duration productNegativeCacheTtl) {
        this.productNegativeCacheTtl = productNegativeCacheTtl;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getProductMaxOutputTokens() {
        return productMaxOutputTokens;
    }

    public void setProductMaxOutputTokens(int productMaxOutputTokens) {
        this.productMaxOutputTokens = productMaxOutputTokens;
    }
}
