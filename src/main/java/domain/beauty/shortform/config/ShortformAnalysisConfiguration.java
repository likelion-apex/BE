package domain.beauty.shortform.config;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableAsync
@EnableConfigurationProperties({
        YouTubeProperties.class,
        OpenAiRoutineProperties.class,
        ShortformAiFallbackProperties.class,
        ShortformProductEnrichmentProperties.class
})
public class ShortformAnalysisConfiguration {

    @Bean
    @Qualifier("youtubeMetadataRestClient")
    RestClient youtubeMetadataRestClient(YouTubeProperties properties) {
        return restClient(properties.getBaseUrl().toString(), properties.getConnectTimeout(), properties.getReadTimeout());
    }

    @Bean
    @Qualifier("shortformOpenAiRestClient")
    RestClient shortformOpenAiRestClient(OpenAiRoutineProperties properties) {
        return restClient(null, properties.getConnectTimeout(), properties.getReadTimeout());
    }

    @Bean(name = "shortformAnalysisExecutor")
    Executor shortformAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(30);
        executor.setThreadNamePrefix("shortform-analysis-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }

    private RestClient restClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}
