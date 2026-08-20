package domain.cosmetic.client;

import domain.inventory.ai.AiProviderUnavailableException;
import domain.inventory.ai.GroqProperties;
import domain.inventory.ai.IngredientAiDetail;
import domain.inventory.ai.InventoryAiJsonSupport;
import domain.inventory.ai.InventoryAiProperties;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 제품명으로 Groq(OpenAI 호환 Chat Completions) API를 호출해 전성분 목록을 받아온다.
 * OpenAI/Gemini가 모두 실패했을 때의 2차 폴백으로 사용된다.
 */
@Slf4j
@Component
public class GroqIngredientClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final GroqProperties groqProperties;
    private final InventoryAiProperties inventoryAiProperties;

    public GroqIngredientClient(
            @Qualifier("inventoryGroqRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            GroqProperties groqProperties,
            InventoryAiProperties inventoryAiProperties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.groqProperties = groqProperties;
        this.inventoryAiProperties = inventoryAiProperties;
    }

    public List<String> fetchIngredientNames(String productName) {
        if (productName == null || productName.isBlank()) {
            return List.of();
        }
        JsonNode payload = completeJson(
                OpenAiIngredientClient.SYSTEM_PROMPT, "제품명: " + productName, "전성분 조회", productName);
        return InventoryAiJsonSupport.parseIngredientNames(payload);
    }

    public Map<String, IngredientAiDetail> fetchIngredientDetails(List<String> ingredientNames) {
        if (ingredientNames == null || ingredientNames.isEmpty()) {
            return Map.of();
        }
        JsonNode payload = completeJson(
                OpenAiIngredientClient.DETAIL_SYSTEM_PROMPT,
                "성분 목록: " + String.join(", ", ingredientNames),
                "배합목적/위험도 조회",
                String.join(",", ingredientNames));
        return InventoryAiJsonSupport.parseIngredientDetails(payload);
    }

    public String fetchBrand(String productName) {
        if (productName == null || productName.isBlank()) {
            return null;
        }
        JsonNode payload = completeJson(
                OpenAiIngredientClient.BRAND_SYSTEM_PROMPT, "제품명: " + productName, "브랜드 조회", productName);
        return InventoryAiJsonSupport.parseBrand(payload);
    }

    private JsonNode completeJson(String systemPrompt, String userPrompt, String action, String context) {
        String apiKey = groqProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiProviderUnavailableException("GROQ_API_KEY가 없습니다.");
        }
        try {
            String requestJson = objectMapper.writeValueAsString(Map.of(
                    "model", groqProperties.getModel(),
                    "temperature", 0,
                    "max_tokens", inventoryAiProperties.getOpenaiMaxOutputTokens(),
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            ));
            String responseJson = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);
            JsonNode response = InventoryAiJsonSupport.readObject(objectMapper, responseJson);
            String content = response == null
                    ? null
                    : response.path("choices").path(0).path("message").path("content").asText(null);
            JsonNode parsed = InventoryAiJsonSupport.readObject(objectMapper, content);
            if (parsed == null || !parsed.isObject()) {
                throw new AiProviderUnavailableException("Groq " + action + " 응답이 비어 있습니다.");
            }
            return parsed;
        } catch (AiProviderUnavailableException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("Groq {} 실패: context={}, message={}", action, context, e.getMessage());
            throw unavailable(e);
        } catch (RuntimeException e) {
            log.warn("Groq {} 파싱 실패: context={}", action, context);
            throw new AiProviderUnavailableException("Groq " + action + " 응답을 해석할 수 없습니다.", e);
        }
    }

    private AiProviderUnavailableException unavailable(RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            if (responseException.getStatusCode().value() == 429) {
                return AiProviderUnavailableException.quota("Groq 호출에 실패했습니다.", exception);
            }
            if (responseException.getStatusCode().is4xxClientError()) {
                return new AiProviderUnavailableException("Groq 요청이 거부되었습니다.", exception);
            }
        }
        return new AiProviderUnavailableException("Groq 호출에 실패했습니다.", exception);
    }
}
