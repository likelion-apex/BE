package domain.inventory.ai;

import domain.cosmetic.client.GroqIngredientClient;
import domain.cosmetic.client.OpenAiIngredientClient;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 전성분/배합목적 조회를 캐시 → OpenAI(1차) → Gemini(폴백1) → Groq(폴백2) 순서로 시도한다.
 * 각 단계는 실제 예외가 발생했을 때만 다음 provider로 넘어가며, 사전 토큰 추정은 사용하지 않는다.
 */
@Slf4j
@Component
public class IngredientAiClient {

    private static final List<AiProvider> ORDER = List.of(AiProvider.OPENAI, AiProvider.GEMINI, AiProvider.GROQ);

    private final OpenAiIngredientClient openAiIngredientClient;
    private final InventoryGeminiJsonClient geminiJsonClient;
    private final GroqIngredientClient groqIngredientClient;
    private final AiProviderSkipGate skipGate;

    public IngredientAiClient(
            OpenAiIngredientClient openAiIngredientClient,
            InventoryGeminiJsonClient geminiJsonClient,
            GroqIngredientClient groqIngredientClient,
            AiProviderSkipGate skipGate) {
        this.openAiIngredientClient = openAiIngredientClient;
        this.geminiJsonClient = geminiJsonClient;
        this.groqIngredientClient = groqIngredientClient;
        this.skipGate = skipGate;
    }

    public List<String> fetchIngredientNames(String productName) {
        if (productName == null || productName.isBlank()) {
            return List.of();
        }
        String userPrompt = "제품명: " + productName;
        for (AiProvider provider : ORDER) {
            if (skipGate.shouldSkip(provider)) {
                continue;
            }
            try {
                return switch (provider) {
                    case OPENAI -> openAiIngredientClient.fetchIngredientNames(productName);
                    case GEMINI -> InventoryAiJsonSupport.parseIngredientNames(
                            geminiJsonClient.generateJson(OpenAiIngredientClient.SYSTEM_PROMPT, userPrompt));
                    case GROQ -> groqIngredientClient.fetchIngredientNames(productName);
                };
            } catch (AiProviderUnavailableException e) {
                log.warn("전성분 {} 실패: productName={}, message={}", provider, productName, e.getMessage());
                skipGate.markFrom(provider, e);
            }
        }
        return List.of();
    }

    public Map<String, IngredientAiDetail> fetchIngredientDetails(List<String> ingredientNames) {
        if (ingredientNames == null || ingredientNames.isEmpty()) {
            return Map.of();
        }
        String userPrompt = "성분 목록: " + String.join(", ", ingredientNames);
        for (AiProvider provider : ORDER) {
            if (skipGate.shouldSkip(provider)) {
                continue;
            }
            try {
                return switch (provider) {
                    case OPENAI -> openAiIngredientClient.fetchIngredientDetails(ingredientNames);
                    case GEMINI -> InventoryAiJsonSupport.parseIngredientDetails(geminiJsonClient.generateJson(
                            OpenAiIngredientClient.DETAIL_SYSTEM_PROMPT, userPrompt));
                    case GROQ -> groqIngredientClient.fetchIngredientDetails(ingredientNames);
                };
            } catch (AiProviderUnavailableException e) {
                log.warn("배합목적/위험도 {} 실패: message={}", provider, e.getMessage());
                skipGate.markFrom(provider, e);
            }
        }
        return Map.of();
    }

    public String inferBrand(String productName) {
        if (productName == null || productName.isBlank()) {
            return null;
        }
        String userPrompt = "제품명: " + productName;
        for (AiProvider provider : ORDER) {
            if (skipGate.shouldSkip(provider)) {
                continue;
            }
            try {
                return switch (provider) {
                    case OPENAI -> openAiIngredientClient.fetchBrand(productName);
                    case GEMINI -> InventoryAiJsonSupport.parseBrand(
                            geminiJsonClient.generateJson(OpenAiIngredientClient.BRAND_SYSTEM_PROMPT, userPrompt));
                    case GROQ -> groqIngredientClient.fetchBrand(productName);
                };
            } catch (AiProviderUnavailableException e) {
                log.warn("브랜드 추론 {} 실패: productName={}, message={}", provider, productName, e.getMessage());
                skipGate.markFrom(provider, e);
            }
        }
        return null;
    }
}
