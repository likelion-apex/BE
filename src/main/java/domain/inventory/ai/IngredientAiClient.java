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
    private final OpenAiSkipGate openAiSkipGate;
    private final InventoryAiProperties inventoryAiProperties;

    public IngredientAiClient(
            OpenAiIngredientClient openAiIngredientClient,
            InventoryGeminiJsonClient geminiJsonClient,
            OpenAiSkipGate openAiSkipGate,
            InventoryAiProperties inventoryAiProperties) {
        this.openAiIngredientClient = openAiIngredientClient;
        this.geminiJsonClient = geminiJsonClient;
        this.openAiSkipGate = openAiSkipGate;
        this.inventoryAiProperties = inventoryAiProperties;
    }

    public List<String> fetchIngredientNames(String productName) {
        if (productName == null || productName.isBlank()) {
            return List.of();
        }
        String userPrompt = "제품명: " + productName;
        try {
            JsonNode payload = geminiJsonClient.generateJson(OpenAiIngredientClient.SYSTEM_PROMPT, userPrompt);
            return InventoryAiJsonSupport.parseIngredientNames(payload);
        } catch (AiProviderUnavailableException geminiFailure) {
            log.warn("전성분 Gemini 실패, OpenAI 폴백 검토: productName={}, message={}",
                    productName, geminiFailure.getMessage());
        }
        if (!shouldCallOpenAi(OpenAiIngredientClient.SYSTEM_PROMPT, userPrompt)) {
            return List.of();
        }
        try {
            return openAiIngredientClient.fetchIngredientNames(productName);
        } catch (AiProviderUnavailableException e) {
            openAiSkipGate.markFrom(e);
            log.warn("전성분 OpenAI 폴백 실패: productName={}, message={}", productName, e.getMessage());
            return List.of();
        }
    }

    public Map<String, List<String>> fetchIngredientPurposes(List<String> ingredientNames) {
        if (ingredientNames == null || ingredientNames.isEmpty()) {
            return Map.of();
        }
        String userPrompt = "성분 목록: " + String.join(", ", ingredientNames);
        try {
            JsonNode payload = geminiJsonClient.generateJson(
                    OpenAiIngredientClient.PURPOSE_SYSTEM_PROMPT, userPrompt);
            return InventoryAiJsonSupport.parsePurposes(payload);
        } catch (AiProviderUnavailableException geminiFailure) {
            log.warn("배합목적 Gemini 실패, OpenAI 폴백 검토: message={}", geminiFailure.getMessage());
        }
        if (!shouldCallOpenAi(OpenAiIngredientClient.PURPOSE_SYSTEM_PROMPT, userPrompt)) {
            return Map.of();
        }
        try {
            return openAiIngredientClient.fetchIngredientPurposes(ingredientNames);
        } catch (AiProviderUnavailableException e) {
            openAiSkipGate.markFrom(e);
            log.warn("배합목적 OpenAI 폴백 실패: message={}", e.getMessage());
            return Map.of();
        }
    }

    private boolean shouldCallOpenAi(String... promptParts) {
        if (openAiSkipGate.shouldSkip()) {
            return false;
        }
        int estimated = InventoryAiTokenEstimator.estimate(promptParts);
        int limit = inventoryAiProperties.getOpenaiMaxInputTokens();
        if (estimated > limit) {
            log.info("예상 입력 토큰 {}이 한도 {}를 넘어 OpenAI 폴백을 건너뜁니다.", estimated, limit);
            return false;
        }
        return true;
    }
}
