package domain.beauty.shortform.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public class OpenAiRoutineProperties {

    private String apiKey = "";
    private URI apiUrl = URI.create("https://api.openai.com/v1/chat/completions");
    private String organizationId = "";
    private String routineModel = "gpt-4o-mini";
    private String routinePromptVersion = "2.0";
    private String productModel = "gpt-4o-mini";
    private String productPromptVersion = "1.0";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(60);
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

    public String getProductPromptVersion() {
        return productPromptVersion;
    }

    public void setProductPromptVersion(String productPromptVersion) {
        this.productPromptVersion = productPromptVersion;
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
