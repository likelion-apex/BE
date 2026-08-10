package feat.apex_BE.cosmetic.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 제품명으로 ChatGPT(OpenAI Chat Completions) API를 호출해 전성분 목록을 받아온다.
 * 공식 식약처 오픈API 중에는 "제품명 -> 전성분"을 제공하는 API가 없어 도입한 보조 수단으로,
 * 모델의 학습 데이터에 기반한 추정치이므로 실제 라벨과 다를 수 있다.
 */
@Slf4j
@Component
public class OpenAiIngredientClient {

    private static final String SYSTEM_PROMPT = """
            당신은 화장품 전성분 정보를 알려주는 어시스턴트입니다.
            사용자가 알려준 화장품 제품명에 대해 실제 제품 라벨에 기재되는 전성분(포함된 모든 성분)을
            한국어 표준 성분명으로, 배합량이 많은 순서대로 나열하세요.
            제품을 확실히 알 수 없으면 빈 배열을 반환하세요.
            반드시 아래 JSON 형식으로만 답변하세요: {"ingredients": ["성분1", "성분2"]}
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String organizationId;

    public OpenAiIngredientClient(
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

    public List<String> fetchIngredientNames(String productName) {
        if (apiKey == null || apiKey.isBlank() || productName == null || productName.isBlank()) {
            return List.of();
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

            return parseIngredients(response, productName);
        } catch (RestClientException e) {
            log.warn("ChatGPT 전성분 조회 실패: productName={}, message={}", productName, e.getMessage());
            return List.of();
        }
    }

    private List<String> parseIngredients(JsonNode response, String productName) {
        if (response == null) {
            return List.of();
        }
        String content = response.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            return List.of();
        }
        try {
            JsonNode parsed = objectMapper.readTree(content);
            JsonNode ingredientsNode = parsed.path("ingredients");
            if (!ingredientsNode.isArray()) {
                return List.of();
            }
            List<String> ingredients = new ArrayList<>();
            ingredientsNode.forEach(node -> {
                String name = node.asText(null);
                if (name != null && !name.isBlank()) {
                    ingredients.add(name.trim());
                }
            });
            return ingredients;
        } catch (Exception e) {
            log.warn("ChatGPT 전성분 응답 파싱 실패: productName={}, content={}", productName, content);
            return List.of();
        }
    }
}
