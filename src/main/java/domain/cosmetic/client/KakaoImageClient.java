package domain.cosmetic.client;

import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class KakaoImageClient {

    private final RestClient restClient;
    private final String apiKey;

    public KakaoImageClient(
            @Value("${kakao.image-search-key}") String apiKey,
            @Value("${kakao.image-search-url}") String baseUrl) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * 제품명으로 카카오 이미지 검색을 수행해 첫 번째 결과의 이미지 URL을 반환한다.
     * API 키가 설정되지 않았거나 호출에 실패하면 null을 반환한다.
     */
    public String searchImageUrl(String query) {
        if (apiKey == null || apiKey.isBlank() || query == null || query.isBlank()) {
            return null;
        }
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("query", query)
                            .queryParam("size", 1)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                return null;
            }
            JsonNode documents = response.path("documents");
            if (documents.isArray() && !documents.isEmpty()) {
                JsonNode imageUrl = documents.get(0).path("image_url");
                return imageUrl.isMissingNode() ? null : imageUrl.asText(null);
            }
        } catch (RestClientException e) {
            log.warn("카카오 이미지 검색 실패: query={}, message={}", query, e.getMessage());
        }
        return null;
    }
}
