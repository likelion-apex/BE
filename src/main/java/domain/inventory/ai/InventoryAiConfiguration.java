package domain.inventory.ai;

import domain.beauty.config.GeminiProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({InventoryAiProperties.class, GeminiProperties.class})
public class InventoryAiConfiguration {

    @Bean
    @Qualifier("inventoryOpenAiRestClient")
    RestClient inventoryOpenAiRestClient(
            @Value("${openai.api-url:https://api.openai.com/v1/chat/completions}") String apiUrl,
            InventoryAiProperties properties) {
        return restClient(apiUrl, properties.getConnectTimeout(), properties.getReadTimeout());
    }

    @Bean
    @Qualifier("inventoryGeminiRestClient")
    RestClient inventoryGeminiRestClient(
            GeminiProperties geminiProperties,
            InventoryAiProperties properties) {
        String baseUrl = geminiProperties.getBaseUrl() == null
                ? "https://generativelanguage.googleapis.com"
                : geminiProperties.getBaseUrl().toString();
        return restClient(baseUrl, properties.getGeminiConnectTimeout(), properties.getGeminiReadTimeout());
    }

    private RestClient restClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
