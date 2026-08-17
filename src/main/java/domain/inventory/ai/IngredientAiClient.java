package domain.inventory.ai;

import domain.cosmetic.client.OpenAiIngredientClient;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Slf4j
@Component
public class IngredientAiClient {

    private final OpenAiIngredientClient openAiIngredientClient;
    private final InventoryGeminiJsonClient geminiJsonClient;

    public IngredientAiClient(
            OpenAiIngredientClient openAiIngredientClient,
            InventoryGeminiJsonClient geminiJsonClient) {
        this.openAiIngredientClient = openAiIngredientClient;
        this.geminiJsonClient = geminiJsonClient;
    }

    public List<String> fetchIngredientNames(String productName) {
        if (productName == null || productName.isBlank()) {
            return List.of();
        }
        try {
            return openAiIngredientClient.fetchIngredientNames(productName);
        } catch (AiProviderUnavailableException e) {
            log.warn("전성분 OpenAI 실패, Gemini로 폴백: productName={}, message={}", productName, e.getMessage());
            try {
                JsonNode payload = geminiJsonClient.generateJson(
                        OpenAiIngredientClient.SYSTEM_PROMPT, "제품명: " + productName);
                return InventoryAiJsonSupport.parseIngredientNames(payload);
            } catch (AiProviderUnavailableException fallback) {
                log.warn("전성분 Gemini 폴백 실패: productName={}, message={}", productName, fallback.getMessage());
                return List.of();
            }
        }
    }

    public Map<String, List<String>> fetchIngredientPurposes(List<String> ingredientNames) {
        if (ingredientNames == null || ingredientNames.isEmpty()) {
            return Map.of();
        }
        try {
            return openAiIngredientClient.fetchIngredientPurposes(ingredientNames);
        } catch (AiProviderUnavailableException e) {
            log.warn("배합목적 OpenAI 실패, Gemini로 폴백: message={}", e.getMessage());
            try {
                JsonNode payload = geminiJsonClient.generateJson(
                        OpenAiIngredientClient.PURPOSE_SYSTEM_PROMPT,
                        "성분 목록: " + String.join(", ", ingredientNames));
                return InventoryAiJsonSupport.parsePurposes(payload);
            } catch (AiProviderUnavailableException fallback) {
                log.warn("배합목적 Gemini 폴백 실패: message={}", fallback.getMessage());
                return Map.of();
            }
        }
    }
}
