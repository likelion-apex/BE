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

    private final InventoryGeminiJsonClient geminiJsonClient;

    public IngredientAiClient(InventoryGeminiJsonClient geminiJsonClient) {
        this.geminiJsonClient = geminiJsonClient;
    }

    public List<String> fetchIngredientNames(String productName) {
        if (productName == null || productName.isBlank()) {
            return List.of();
        }
        try {
            JsonNode payload = geminiJsonClient.generateJson(
                    OpenAiIngredientClient.SYSTEM_PROMPT, "제품명: " + productName);
            return InventoryAiJsonSupport.parseIngredientNames(payload);
        } catch (AiProviderUnavailableException e) {
            log.warn("전성분 Gemini 실패: productName={}, message={}", productName, e.getMessage());
            return List.of();
        }
    }

    public Map<String, List<String>> fetchIngredientPurposes(List<String> ingredientNames) {
        if (ingredientNames == null || ingredientNames.isEmpty()) {
            return Map.of();
        }
        try {
            JsonNode payload = geminiJsonClient.generateJson(
                    OpenAiIngredientClient.PURPOSE_SYSTEM_PROMPT,
                    "성분 목록: " + String.join(", ", ingredientNames));
            return InventoryAiJsonSupport.parsePurposes(payload);
        } catch (AiProviderUnavailableException e) {
            log.warn("배합목적 Gemini 실패: message={}", e.getMessage());
            return Map.of();
        }
    }
}
