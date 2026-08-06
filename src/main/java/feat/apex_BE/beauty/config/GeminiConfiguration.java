package feat.apex_BE.beauty.config;

import java.net.http.HttpClient;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import feat.apex_BE.beauty.domain.BeautyRoutineAnalysisResult;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiConfiguration {

	@Bean
	RestClient geminiRestClient(GeminiProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(properties.getConnectTimeout())
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.getReadTimeout());

		return RestClient.builder()
			.baseUrl(properties.getBaseUrl().toString())
			.requestFactory(requestFactory)
			.build();
	}

	@Bean
	Cache<String, BeautyRoutineAnalysisResult> beautyRoutineAnalysisCache(GeminiProperties properties) {
		return Caffeine.newBuilder()
			.maximumSize(properties.getCacheMaximumSize())
			.expireAfterWrite(properties.getCacheTtl())
			.build();
	}
}
