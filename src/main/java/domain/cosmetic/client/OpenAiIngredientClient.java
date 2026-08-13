package domain.cosmetic.client;

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
import java.util.LinkedHashMap;
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

    private static final String PURPOSE_SYSTEM_PROMPT = """
            당신은 화장품 성분의 배합목적(용도)을 알려주는 어시스턴트입니다.
            사용자가 알려준 성분 목록 각각에 대해 화장품 원료로서의 배합목적을 1~3개씩 한국어로 나열하세요
            (예: "피부 보습", "피부 컨디셔닝", "기제(용매)", "피부 진정").
            성분의 배합목적을 확실히 알 수 없으면 해당 성분은 빈 배열로 응답하세요.
            반드시 아래 JSON 형식으로만, 요청받은 성분 개수와 이름을 그대로 유지하여 답변하세요:
            {"ingredients": [{"name": "성분1", "purposes": ["용도1", "용도2"]}]}
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

    /**
     * 성분명 목록을 받아 각 성분의 배합목적(용도) 목록을 조회한다.
     * 결과는 요청 순서를 보존한 Map(성분명 -> 배합목적 목록)으로 반환하며, 실패하거나
     * 응답에 없는 성분은 결과에 포함되지 않는다.
     */
    public Map<String, List<String>> fetchIngredientPurposes(List<String> ingredientNames) {
        if (apiKey == null || apiKey.isBlank() || ingredientNames == null || ingredientNames.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "temperature", 0,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", PURPOSE_SYSTEM_PROMPT),
                            Map.of("role", "user", "content", "성분 목록: " + String.join(", ", ingredientNames))
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

            return parsePurposes(response, ingredientNames);
        } catch (RestClientException e) {
            log.warn("ChatGPT 배합목적 조회 실패: ingredientNames={}, message={}", ingredientNames, e.getMessage());
            return Map.of();
        }
    }

    private Map<String, List<String>> parsePurposes(JsonNode response, List<String> ingredientNames) {
        if (response == null) {
            return Map.of();
        }
        String content = response.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode parsed = objectMapper.readTree(content);
            JsonNode ingredientsNode = parsed.path("ingredients");
            if (!ingredientsNode.isArray()) {
                return Map.of();
            }
            Map<String, List<String>> result = new LinkedHashMap<>();
            ingredientsNode.forEach(node -> {
                String name = node.path("name").asText(null);
                if (name == null || name.isBlank()) {
                    return;
                }
                List<String> purposes = new ArrayList<>();
                JsonNode purposesNode = node.path("purposes");
                if (purposesNode.isArray()) {
                    purposesNode.forEach(purposeNode -> {
                        String value = purposeNode.asText(null);
                        if (value != null && !value.isBlank()) {
                            purposes.add(value.trim());
                        }
                    });
                }
                result.put(name.trim(), purposes);
            });
            return result;
        } catch (Exception e) {
            log.warn("ChatGPT 배합목적 응답 파싱 실패: ingredientNames={}, content={}", ingredientNames, content);
            return Map.of();
        }
    }
}
