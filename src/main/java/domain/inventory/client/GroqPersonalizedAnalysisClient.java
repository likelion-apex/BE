package domain.inventory.client;

import domain.inventory.ai.AiProviderUnavailableException;
import domain.inventory.ai.GroqProperties;
import domain.inventory.ai.InventoryAiJsonSupport;
import domain.inventory.ai.InventoryAiProperties;
import domain.member.SkinConcern;
import domain.member.SkinType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 제품명/전성분과 회원의 피부타입·피부고민을 바탕으로 Groq(OpenAI 호환 API)에게 맞춤 분석을 요청한다.
 * OpenAI/Gemini가 모두 실패했을 때의 2차 폴백으로 사용된다.
 */
@Slf4j
@Component
public class GroqPersonalizedAnalysisClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final GroqProperties groqProperties;
    private final InventoryAiProperties inventoryAiProperties;

    public GroqPersonalizedAnalysisClient(
            @Qualifier("inventoryGroqRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            GroqProperties groqProperties,
            InventoryAiProperties inventoryAiProperties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.groqProperties = groqProperties;
        this.inventoryAiProperties = inventoryAiProperties;
    }

    public PersonalizedAnalysisResult analyze(
            String productName, List<String> ingredientNames, SkinType skinType, Set<SkinConcern> skinConcerns) {
        if (productName == null || productName.isBlank()) {
            return null;
        }
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
                            Map.of("role", "system", "content", OpenAiPersonalizedAnalysisClient.SYSTEM_PROMPT),
                            Map.of("role", "user", "content",
                                    OpenAiPersonalizedAnalysisClient.buildUserContent(
                                            productName, ingredientNames, skinType, skinConcerns))
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
            PersonalizedAnalysisResult result = OpenAiPersonalizedAnalysisClient.parseResult(parsed);
            if (result == null) {
                throw new AiProviderUnavailableException("Groq 맞춤 분석 응답이 비어 있습니다.");
            }
            return result;
        } catch (AiProviderUnavailableException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("Groq 맞춤 분석 실패: productName={}, message={}", productName, e.getMessage());
            throw InventoryAiJsonSupport.mapToUnavailable("Groq 맞춤 분석", e);
        } catch (RuntimeException e) {
            log.warn("Groq 맞춤 분석 응답 파싱 실패: productName={}", productName);
            throw new AiProviderUnavailableException("Groq 맞춤 분석 응답을 해석할 수 없습니다.", e);
        }
    }
}
