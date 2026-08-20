package domain.inventory.ai;

import domain.beauty.config.GeminiProperties;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Google AI Studio generateContent로 JSON 객체 응답을 받는다. 숏폼 interactions API와 분리한다.
 */
@Slf4j
@Component
public class InventoryGeminiJsonClient {

    private final RestClient restClient;
    private final GeminiProperties geminiProperties;
    private final InventoryAiProperties inventoryAiProperties;
    private final ObjectMapper objectMapper;

    public InventoryGeminiJsonClient(
            @Qualifier("inventoryGeminiRestClient") RestClient restClient,
            GeminiProperties geminiProperties,
            InventoryAiProperties inventoryAiProperties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.geminiProperties = geminiProperties;
        this.inventoryAiProperties = inventoryAiProperties;
        this.objectMapper = objectMapper;
    }

    public JsonNode generateJson(String systemPrompt, String userPrompt) {
        String apiKey = geminiProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiProviderUnavailableException("GEMINI_API_KEY가 없습니다.");
        }
        try {
            Map<String, Object> body = Map.of(
                    "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", userPrompt))
                    )),
                    "generationConfig", Map.of(
                            "temperature", 0,
                            "maxOutputTokens", inventoryAiProperties.getOpenaiMaxOutputTokens(),
                            "responseMimeType", "application/json"
                    )
            );
            String responseJson = restClient.post()
                    .uri("/v1beta/models/" + geminiProperties.getModel() + ":generateContent")
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            JsonNode response = InventoryAiJsonSupport.readObject(objectMapper, responseJson);
            String text = extractText(response);
            JsonNode parsed = InventoryAiJsonSupport.readObject(objectMapper, text);
            if (parsed == null || parsed.isMissingNode() || !parsed.isObject()) {
                throw new AiProviderUnavailableException("Gemini JSON 응답이 비어 있습니다.");
            }
            return parsed;
        } catch (AiProviderUnavailableException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("Gemini generateContent 실패: message={}", e.getMessage());
            throw InventoryAiJsonSupport.mapToUnavailable("Gemini", e);
        } catch (RuntimeException e) {
            log.warn("Gemini JSON 파싱 실패: message={}", e.getMessage());
            throw new AiProviderUnavailableException("Gemini JSON 응답을 해석할 수 없습니다.", e);
        }
    }

    private String extractText(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        parts.forEach(part -> {
            String value = part.path("text").asText(null);
            if (value != null) {
                text.append(value);
            }
        });
        return text.toString();
    }
}
