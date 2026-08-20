package domain.cosmetic.client;

import domain.inventory.ai.AiProviderUnavailableException;
import domain.inventory.ai.IngredientAiDetail;
import domain.inventory.ai.InventoryAiJsonSupport;
import domain.inventory.ai.InventoryAiProperties;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 제품명으로 ChatGPT(OpenAI Chat Completions) API를 호출해 전성분 목록을 받아온다.
 * 할당량 소진·타임아웃 등은 {@link AiProviderUnavailableException}으로 올려 호출측이 Gemini로 넘긴다.
 */
@Slf4j
@Component
public class OpenAiIngredientClient {

    public static final String SYSTEM_PROMPT = """
            당신은 화장품 전성분 정보를 알려주는 어시스턴트입니다.
            사용자가 알려준 화장품 제품명에 대해 실제 제품 라벨에 기재되는 전성분(포함된 모든 성분)을
            한국어 표준 성분명으로, 배합량이 많은 순서대로 나열하세요.
            반드시 아래 JSON 형식으로만 답변하세요: {"ingredients": ["성분1", "성분2"]}
            """;

    public static final String DETAIL_SYSTEM_PROMPT = """
            당신은 화장품 성분의 배합목적(용도)과 위험도를 알려주는 어시스턴트입니다.
            사용자가 알려준 성분 목록 각각에 대해 다음을 판단하세요:
            1. 화장품 원료로서의 배합목적을 1~3개씩 한국어로 나열하세요
               (예: "피부 보습", "피부 컨디셔닝", "기제(용매)", "피부 진정").
               완전히 확신할 수 없어도 화장품 성분 지식을 바탕으로 가장 가능성 높은 배합목적을
               최소 1개는 추정해서 반드시 채우세요. 
            2. 피부 자극/알레르기 유발 가능성 등을 고려한 일반적인 위험도를 "LOW"(낮음, 안전),
               "MEDIUM"(중간, 주의 필요), "HIGH"(높음, 자극/위험 가능성 높음) 중 하나로 판단하세요.
               확신이 없으면 "MEDIUM"으로 응답하세요.
            반드시 아래 JSON 형식으로만, 요청받은 성분 개수와 이름을 그대로 유지하여 답변하세요:
            {"ingredients": [{"name": "성분1", "purposes": ["용도1", "용도2"], "riskLevel": "LOW"}]}
            """;

    public static final String BRAND_SYSTEM_PROMPT = """
            당신은 화장품 제품명에서 브랜드명을 판별하는 어시스턴트입니다.
            사용자가 알려준 제품명을 보고 실제로 존재하는 화장품 브랜드 중 가장 가능성 높은 브랜드명을
            한국에서 통용되는 표시명으로 답하세요. 브랜드를 전혀 특정할 수 없으면 null을 반환하세요.
            반드시 아래 JSON 형식으로만 답변하세요: {"brand": "브랜드명"}
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final InventoryAiProperties inventoryAiProperties;
    private final String apiKey;
    private final String model;
    private final String organizationId;

    public OpenAiIngredientClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model,
            @Value("${openai.organization-id:}") String organizationId,
            @Qualifier("inventoryOpenAiRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            InventoryAiProperties inventoryAiProperties) {
        this.apiKey = apiKey;
        this.model = model;
        this.organizationId = organizationId;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.inventoryAiProperties = inventoryAiProperties;
    }

    public List<String> fetchIngredientNames(String productName) {
        if (productName == null || productName.isBlank()) {
            return List.of();
        }
        JsonNode payload = completeJson(SYSTEM_PROMPT, "제품명: " + productName, "전성분 조회", productName);
        return InventoryAiJsonSupport.parseIngredientNames(payload);
    }

    public Map<String, IngredientAiDetail> fetchIngredientDetails(List<String> ingredientNames) {
        if (ingredientNames == null || ingredientNames.isEmpty()) {
            return Map.of();
        }
        JsonNode payload = completeJson(
                DETAIL_SYSTEM_PROMPT,
                "성분 목록: " + String.join(", ", ingredientNames),
                "배합목적/위험도 조회",
                String.join(",", ingredientNames));
        return InventoryAiJsonSupport.parseIngredientDetails(payload);
    }

    public String fetchBrand(String productName) {
        if (productName == null || productName.isBlank()) {
            return null;
        }
        JsonNode payload = completeJson(BRAND_SYSTEM_PROMPT, "제품명: " + productName, "브랜드 조회", productName);
        return InventoryAiJsonSupport.parseBrand(payload);
    }

    private JsonNode completeJson(String systemPrompt, String userPrompt, String action, String context) {
        requireApiKey();
        try {
            String requestJson = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "temperature", 0,
                    "max_tokens", inventoryAiProperties.getOpenaiMaxOutputTokens(),
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            ));
            String responseJson = restClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .headers(headers -> {
                        if (organizationId != null && !organizationId.isBlank()) {
                            headers.set("OpenAI-Organization", organizationId);
                        }
                    })
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
                throw new AiProviderUnavailableException("OpenAI " + action + " 응답이 비어 있습니다.");
            }
            return parsed;
        } catch (AiProviderUnavailableException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("ChatGPT {} 실패: context={}, message={}", action, context, e.getMessage());
            throw InventoryAiJsonSupport.mapToUnavailable("OpenAI", e);
        } catch (RuntimeException e) {
            log.warn("ChatGPT {} 파싱 실패: context={}", action, context);
            throw new AiProviderUnavailableException("OpenAI " + action + " 응답을 해석할 수 없습니다.", e);
        }
    }

    private void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiProviderUnavailableException("OPENAI_API_KEY가 없습니다.");
        }
    }
}
