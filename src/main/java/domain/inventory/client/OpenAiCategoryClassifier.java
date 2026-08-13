package domain.inventory.client;

import domain.inventory.ProductCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * 제품명으로 ChatGPT(OpenAI Chat Completions) API를 호출해 화장품 카테고리를 자동 분류한다.
 * 인벤토리에 신규 상품이 처음 등록될 때 한 번만 호출되며, 결과는 Product에 저장되어 이후 재사용된다.
 */
@Slf4j
@Component
public class OpenAiCategoryClassifier {

    private static final String SYSTEM_PROMPT = """
            당신은 화장품을 카테고리로 분류하는 어시스턴트입니다.
            사용자가 알려준 화장품 제품명을 보고 다음 카테고리 중 하나로 정확히 분류하세요:
            TONER, SERUM, CREAM, ESSENCE, LOTION, SUNCREAM, CLEANSER, MASK, ETC
            제품 종류를 확실히 알 수 없으면 ETC로 분류하세요.
            반드시 아래 JSON 형식으로만 답변하세요: {"category": "SERUM"}
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String organizationId;

    public OpenAiCategoryClassifier(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.api-url}") String apiUrl,
            @Value("${openai.model}") String model,
            @Value("${openai.organization-id:}") String organizationId,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.organizationId = organizationId;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(apiUrl).build();
    }

    public ProductCategory classify(String productName) {
        if (apiKey == null || apiKey.isBlank() || productName == null || productName.isBlank()) {
            return ProductCategory.ETC;
        }
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "temperature", 0,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", "제품명: " + productName)
                    )
            );

            JsonNode response = restClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .headers(headers -> {
                        if (organizationId != null && !organizationId.isBlank()) {
                            headers.set("OpenAI-Organization", organizationId);
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            return parseCategory(response, productName);
        } catch (RestClientException e) {
            log.warn("ChatGPT 카테고리 분류 실패: productName={}, message={}", productName, e.getMessage());
            return ProductCategory.ETC;
        }
    }

    private ProductCategory parseCategory(JsonNode response, String productName) {
        if (response == null) {
            return ProductCategory.ETC;
        }
        String content = response.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            return ProductCategory.ETC;
        }
        try {
            JsonNode parsed = objectMapper.readTree(content);
            String category = parsed.path("category").asText(null);
            if (category == null || category.isBlank()) {
                return ProductCategory.ETC;
            }
            return ProductCategory.valueOf(category.trim().toUpperCase());
        } catch (Exception e) {
            log.warn("ChatGPT 카테고리 분류 응답 파싱 실패: productName={}, content={}", productName, content);
            return ProductCategory.ETC;
        }
    }
}
